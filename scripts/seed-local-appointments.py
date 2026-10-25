#!/usr/bin/env python3
"""Local agenda fixtures through the authenticated API; no direct database writes."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import getpass
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler, ProxyHandler
from zoneinfo import ZoneInfo


class SeedError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Api:
    def __init__(self, base_url, cookie=None):
        url = urlsplit(base_url)
        if (url.scheme not in ("http", "https") or url.hostname not in
                ("localhost", "127.0.0.1", "::1") or url.username or url.password
                or url.query or url.fragment):
            raise SeedError("Solo se permite una API local (localhost/127.0.0.1/::1).")
        self.base = base_url.rstrip("/") + "/api/v1"
        self.headers = {"Accept": "application/json"}
        if cookie:
            self.headers["Cookie"] = cookie
        else:
            raise SeedError("Falta TURNERO_SESSION_COOKIE (cabecera Cookie de una sesión local).")
        self.opener = build_opener(NoRedirect(), ProxyHandler({}))

    def request(self, path, params=None, body=None):
        url = self.base + path + ("?" + urlencode(params) if params else "")
        headers = dict(self.headers)
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        try:
            with self.opener.open(Request(url, data=data, headers=headers), timeout=30) as response:
                return json.load(response)
        except HTTPError as error:
            # Do not echo tokens, customer data or server error bodies.
            raise SeedError(f"HTTP {error.code} en {path}; ejecución detenida. Revisar sesión/permisos/reglas.") from None
        except (URLError, TimeoutError):
            raise SeedError(f"No se pudo completar {path}; revisar servicios locales. No reintentar POST a ciegas.") from None

    def pages(self, path, **params):
        result = []
        number = 0
        total = None
        while True:
            response = self.request(path, {**params, "page": number, "size": 100})
            page = response["page"]
            if page["number"] != number or (total is not None and total != page["total_elements"]):
                raise SeedError("El listado cambió durante la lectura; volver a simular.")
            total = page["total_elements"]
            result.extend(response["data"])
            if number + 1 >= page["total_pages"]:
                break
            if not response["data"]:
                raise SeedError("Página vacía intermedia; ejecución detenida.")
            number += 1
        if len(result) != total or len({row["id"] for row in result}) != total:
            raise SeedError("Listado incompleto o duplicado; ejecución detenida.")
        return result


def instant(value, zone):
    value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if value.tzinfo is None:
        # Match the read contract: earlier offset at an overlap; advance gaps.
        value = value.replace(tzinfo=zone, fold=0)
    return value.astimezone(timezone.utc)


def overlaps(start, end, appointment, zone):
    return start < instant(appointment["ends_at"], zone) and end > instant(appointment["starts_at"], zone)


def load_appointments(api, days):
    return [row for day in days for row in api.pages("/appointments", date=day.isoformat())]


def candidate_slots(api, days, staff, zone, cutoff):
    candidates = {day: [] for day in days}
    for member in staff:
        services = api.request(f'/staff-members/{member["id"]}/service-offerings')
        for service in services:
            if service["status"] != "ACTIVE" or service["duration_minutes"] <= 0 or service["price_cents"] < 0:
                continue
            slots = api.request("/availability/slots", {"from": days[0].isoformat(), "to": days[-1].isoformat(),
                                "staff_member_id": member["id"], "service_offering_id": service["id"]})
            for slot in slots:
                start, end = instant(slot["startsAt"], zone), instant(slot["endsAt"], zone)
                local = start.astimezone(zone)
                # Avoid ambiguous/nonexistent wall clocks in this local-only fixture utility.
                invalid_wall_time = False
                for field, resolved in (("startsAt", start), ("endsAt", end)):
                    naive = datetime.fromisoformat(slot[field])
                    if naive.tzinfo is None and (resolved.astimezone(zone).replace(tzinfo=None) != naive or
                            naive.replace(tzinfo=zone, fold=0).utcoffset() != naive.replace(tzinfo=zone, fold=1).utcoffset()):
                        invalid_wall_time = True
                if invalid_wall_time:
                    continue
                if (slot["available"] and local.date() in candidates and start > cutoff and
                        end - start == timedelta(minutes=service["duration_minutes"])):
                    candidates[local.date()].append((start, end, member["id"], service))
    # Earliest finish first maximizes useful capacity; stable ties distribute staff/services.
    for values in candidates.values():
        values.sort(key=lambda item: (item[1], item[0], item[2], item[3]["id"]))
    return candidates


def plan(candidates, customers, existing, count, marker, zone):
    fixtures = [row for row in existing if row.get("internal_notes") == marker]
    if len(fixtures) >= count:
        return [], len(fixtures)
    occupied = list(existing)
    planned = []
    customer_use = Counter(row["customer_id"] for row in fixtures)
    day_use = Counter(instant(row["starts_at"], zone).astimezone(zone).date() for row in fixtures)
    queues = {day: list(values) for day, values in candidates.items()}
    while len(planned) + len(fixtures) < count:
        chosen = None
        for day in sorted(queues, key=lambda day: (day_use[day], day)):
            while queues[day]:
                start, end, staff_id, service = queues[day].pop(0)
                if any(row["staff_member_id"] == staff_id and overlaps(start, end, row, zone) for row in occupied):
                    continue
                customer = next((c for c in sorted(customers, key=lambda c: (customer_use[c["id"]], c["id"]))
                                 if not any(row["customer_id"] == c["id"] and overlaps(start, end, row, zone) for row in occupied)), None)
                if customer is None:
                    continue
                chosen = {"customerId": customer["id"], "staffMemberId": staff_id,
                          "serviceOfferingId": service["id"],
                          "startsAt": start.astimezone(zone).replace(tzinfo=None).isoformat(),
                          "endsAt": end.astimezone(zone).replace(tzinfo=None).isoformat(),
                          "durationMinutes": service["duration_minutes"], "priceCents": service["price_cents"],
                          "status": "PENDING" if (len(fixtures) + len(planned)) % 2 == 0 else "CONFIRMED",
                          "source": "ADMIN", "internalNotes": marker}
                planned.append(chosen)
                occupied.append({"customer_id": customer["id"], "staff_member_id": staff_id,
                                 "starts_at": start.isoformat(), "ends_at": end.isoformat()})
                customer_use[customer["id"]] += 1
                day_use[day] += 1
                break
            if chosen:
                break
        if chosen is None:
            capacity = len(fixtures) + len(planned)
            raise SeedError(f"Capacidad encontrada: {capacity}/{count}. No se creó nada en esta ejecución. "
                            "Revisar horarios, días cerrados, asignaciones o usar --count menor; no se extiende el rango.")
    return planned, len(fixtures)


def seed(api, business_id, count=106, days_count=3, apply=False, now=None, calendar_days=False):
    business = api.request("/business")
    if business["id"] != business_id:
        raise SeedError("La sesión pertenece a otro negocio; no se modificó nada.")
    zone = ZoneInfo(business["timezone"])
    now = now or datetime.now(timezone.utc)
    today = now.astimezone(zone).date()
    search_days = [today + timedelta(days=i) for i in range(days_count if calendar_days else 31)]
    # Keep a stable batch even when its own bookings consume a day's last available slots.
    version = "v2" if calendar_days else "v3-available"
    marker = f"Fixture local agenda {version}:{business_id}:{today}:{days_count}"
    staff = [row for row in api.pages("/staff-members", status="ACTIVE")
             if row["status"] == "ACTIVE" and not row.get("deleted_at")]
    customers = [row for row in api.pages("/customers", status="ACTIVE") if row["status"] == "ACTIVE"]
    if not staff or not customers:
        raise SeedError("Faltan profesionales o clientes activos. Completar aprovisionamiento antes de sembrar.")
    # Read the preceding day too: an earlier appointment may end inside today's opening window.
    existing = load_appointments(api, [today - timedelta(days=1), *search_days])
    candidates = candidate_slots(api, search_days, staff, zone, now + timedelta(minutes=15))
    if calendar_days:
        days = search_days
    else:
        batch_days = {instant(a["starts_at"], zone).astimezone(zone).date() for a in existing
                      if a.get("internal_notes") == marker}
        days = []
        for day in search_days:
            usable = day in batch_days or any(
                not any(a["staff_member_id"] == member and overlaps(start, end, a, zone) for a in existing)
                and any(not any(a["customer_id"] == c["id"] and overlaps(start, end, a, zone) for a in existing)
                        for c in customers)
                for start, end, member, _service in candidates[day])
            if usable:
                days.append(day)
            if len(days) == days_count:
                break
        if len(days) < days_count:
            raise SeedError(f"Solo hay {len(days)}/{days_count} días con disponibilidad dentro de los próximos 31 días. "
                            "No se creó nada; revisar catálogo/horarios/excepciones o reducir --days.")
        skipped = [str(day) for day in search_days if day < days[-1] and day not in days]
        if skipped:
            print("Días omitidos sin huecos futuros utilizables:", ", ".join(skipped))
        candidates = {day: candidates[day] for day in days}
    planned, already = plan(candidates, customers, existing, count, marker, zone)
    summary = Counter(row["startsAt"][:10] for row in planned)
    print(f"Negocio {business_id} · zona {zone.key} · fechas elegidas: {', '.join(map(str, days))}")
    print(f"Objetivo {count}; existentes del lote {already}; nuevos {len(planned)}; distribución nueva {dict(sorted(summary.items()))}")
    if not apply:
        print("SIMULACIÓN: sin escrituras. Agregar --apply para crear el plan con POST /appointments.")
        return planned
    created = 0
    created_ids = set()
    try:
        for row in planned:
            if instant(row["startsAt"], zone) <= datetime.now(timezone.utc) + timedelta(minutes=1):
                raise SeedError("El plan quedó desactualizado; volver a simular para no crear horarios pasados.")
            # Re-read catalog references, availability and all-state/customer overlaps before every POST.
            if api.request(f'/staff-members/{row["staffMemberId"]}')["status"] != "ACTIVE":
                raise SeedError("Cambió el estado del profesional; volver a simular.")
            if api.request(f'/customers/{row["customerId"]}')["status"] != "ACTIVE":
                raise SeedError("Cambió el estado del cliente; volver a simular.")
            services = api.request(f'/staff-members/{row["staffMemberId"]}/service-offerings')
            if not any(s["id"] == row["serviceOfferingId"] and s["status"] == "ACTIVE" and
                       s["duration_minutes"] == row["durationMinutes"] and s["price_cents"] == row["priceCents"] for s in services):
                raise SeedError("Cambió el servicio/asignación; volver a simular.")
            slots = api.request("/availability/slots", {"date": row["startsAt"][:10],
                                "staff_member_id": row["staffMemberId"], "service_offering_id": row["serviceOfferingId"]})
            if not any(s["available"] and instant(s["startsAt"], zone) == instant(row["startsAt"], zone) and instant(s["endsAt"], zone) == instant(row["endsAt"], zone) for s in slots):
                raise SeedError("Cambió la disponibilidad; volver a simular.")
            fresh = load_appointments(api, [datetime.fromisoformat(row["startsAt"]).date() - timedelta(days=1),
                                            datetime.fromisoformat(row["startsAt"]).date()])
            start, end = instant(row["startsAt"], zone), instant(row["endsAt"], zone)
            if any((a["staff_member_id"] == row["staffMemberId"] or a["customer_id"] == row["customerId"])
                   and overlaps(start, end, a, zone) for a in fresh):
                raise SeedError("Apareció un cruce; volver a simular.")
            saved = api.request("/appointments", body=row)
            created_ids.add(saved["id"])
            created += 1
    except SeedError as error:
        raise SeedError(f"{error} Altas confirmadas en esta ejecución: {created}. "
                        "No se borran datos; reejecutar el mismo lote para reconciliar/reanudar.") from None
    actual = load_appointments(api, [today - timedelta(days=1), *search_days])
    found = [a for a in actual if a.get("internal_notes") == marker]
    if len(found) != already + created:
        raise SeedError("La verificación final no coincide; revisar el lote antes de continuar.")
    for row in actual:
        if row["id"] in created_ids:
            if any(other["id"] != row["id"] and
                   (other["staff_member_id"] == row["staff_member_id"] or other["customer_id"] == row["customer_id"]) and
                   overlaps(instant(row["starts_at"], zone), instant(row["ends_at"], zone), other, zone) for other in actual):
                raise SeedError("Verificación final: apareció un cruce. Revisar el lote; no se borraron datos.")
    print(f"Creados y verificados: {created}; total del lote: {len(found)}. Sin borrar ni reprogramar turnos anteriores.")
    return planned


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080", help="API local o http://localhost:3000/api/backend para BFF")
    parser.add_argument("--business-id", type=int, required=True, help="ID esperado del negocio de la sesión")
    parser.add_argument("--count", type=int, default=106, help="Cantidad objetivo del lote, no por día")
    parser.add_argument("--days", type=int, default=3, help="Días con disponibilidad futura desde hoy del negocio (default 3; busca hasta 31 días)")
    parser.add_argument("--calendar-days", action="store_true", help="Usar días calendario consecutivos sin saltar días sin disponibilidad")
    parser.add_argument("--apply", action="store_true", help="Crear después de comprobar la capacidad completa")
    args = parser.parse_args()
    if args.count < 1 or args.business_id < 1 or not 1 <= args.days <= 30:
        parser.error("business-id/count positivos; days entre 1 y 30")
    try:
        if os.environ.get("SPRING_PROFILES_ACTIVE", "dev") != "dev":
            raise SeedError("Solo permitido con perfil dev.")
        cookie = os.environ.get("TURNERO_SESSION_COOKIE")
        if not cookie and sys.stdin.isatty():
            cookie = getpass.getpass("Cookie de sesión local, nombre=valor (oculta): ")
        seed(Api(args.base_url, cookie), args.business_id, args.count, args.days, args.apply, calendar_days=args.calendar_days)
    except (SeedError, KeyError, ValueError, TypeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
