import importlib.util
import io
from contextlib import redirect_stdout
from datetime import datetime, timedelta, timezone
from pathlib import Path
import unittest
from unittest.mock import Mock
from urllib.error import HTTPError
from zoneinfo import ZoneInfo

spec = importlib.util.spec_from_file_location("seed", Path(__file__).with_name("seed-local-appointments.py"))
seed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(seed)
NOW = datetime(2029, 10, 23, 10, tzinfo=timezone.utc)  # fixed future instant for apply tests
ZONE = ZoneInfo("America/Argentina/Buenos_Aires")


class FakeApi:
    def __init__(self):
        self.rows = []
        self.posts = 0
        self.fail_after = None
        self.staff = [{"id": 1, "status": "ACTIVE"}, {"id": 2, "status": "ACTIVE"}]
        self.customers = [{"id": i, "status": "ACTIVE"} for i in range(1, 5)]
        self.services = [{"id": 1, "status": "ACTIVE", "duration_minutes": 30, "price_cents": 5000}]
        self.closed = set()

    def pages(self, path, **params):
        if path == "/staff-members":
            return self.staff
        if path == "/customers":
            return self.customers
        return [a for a in self.rows if a["starts_at"][:10] == params["date"]]

    def request(self, path, params=None, body=None):
        if path == "/business":
            return {"id": 1, "timezone": ZONE.key}
        if path.endswith("/service-offerings"):
            return self.services
        if path.startswith("/staff-members/"):
            return {"status": "ACTIVE"}
        if path.startswith("/customers/"):
            return {"status": "ACTIVE"}
        if path == "/availability/slots":
            start = datetime.fromisoformat(params.get("from", params.get("date")))
            end = datetime.fromisoformat(params.get("to", params.get("date")))
            slots = []
            while start <= end:
                if start.date() not in self.closed:
                    for i in range(18):
                        begin = start.replace(hour=9) + timedelta(minutes=30*i)
                        finish = begin + timedelta(minutes=30)
                        if not any(a["staff_member_id"] == params["staff_member_id"] and
                                   seed.overlaps(seed.instant(begin.isoformat(), ZONE), seed.instant(finish.isoformat(), ZONE), a, ZONE)
                                   for a in self.rows if a["status"] in ("PENDING", "CONFIRMED")):
                            slots.append({"startsAt": begin.isoformat(), "endsAt": finish.isoformat(), "available": True})
                start += timedelta(days=1)
            return slots
        if path == "/appointments":
            if self.fail_after is not None and self.posts == self.fail_after:
                raise seed.SeedError("Simulated HTTP 409")
            self.posts += 1
            self.rows.append({"id": self.posts, "customer_id": body["customerId"],
                              "staff_member_id": body["staffMemberId"], "service_offering_id": body["serviceOfferingId"],
                              "starts_at": body["startsAt"], "ends_at": body["endsAt"],
                              "status": body["status"], "internal_notes": body["internalNotes"]})
            return {"id": self.posts}
        raise AssertionError(path)


class SeedTests(unittest.TestCase):
    def run_seed(self, api, count=106, apply=False):
        with redirect_stdout(io.StringIO()):
            return seed.seed(api, 1, count=count, now=NOW, apply=apply)

    def test_dry_run_106_three_days_no_mutations_and_no_overlaps(self):
        api = FakeApi()
        plan = self.run_seed(api)
        self.assertEqual(len(plan), 106)
        self.assertEqual(api.posts, 0)
        self.assertEqual(len({p["startsAt"][:10] for p in plan}), 3)
        for index, a in enumerate(plan):
            self.assertEqual(a["durationMinutes"], 30)
            self.assertEqual(a["priceCents"], 5000)
            self.assertIn(a["status"], ("PENDING", "CONFIRMED"))
            self.assertGreater(seed.instant(a["startsAt"], ZONE), NOW)
            for b in plan[index+1:]:
                if a["customerId"] == b["customerId"] or a["staffMemberId"] == b["staffMemberId"]:
                    self.assertFalse(a["startsAt"] < b["endsAt"] and a["endsAt"] > b["startsAt"])

    def test_apply_and_second_run_are_idempotent(self):
        api = FakeApi()
        self.run_seed(api, count=6, apply=True)
        self.run_seed(api, count=6, apply=True)
        self.assertEqual(api.posts, 6)

    def test_partial_failure_is_resumable(self):
        api = FakeApi()
        api.fail_after = 2
        with self.assertRaisesRegex(seed.SeedError, "Altas confirmadas.*2"):
            self.run_seed(api, count=6, apply=True)
        api.fail_after = None
        self.run_seed(api, count=6, apply=True)
        self.assertEqual(api.posts, 6)

    def test_capacity_failure_does_not_post(self):
        api = FakeApi()
        api.closed = {(NOW + timedelta(days=i)).date() for i in range(31)}
        with self.assertRaisesRegex(seed.SeedError, "Solo hay 0/3 días"):
            self.run_seed(api, apply=True)
        self.assertEqual(api.posts, 0)

    def test_missing_catalog_does_not_create_people(self):
        for collection in ("staff", "customers", "services"):
            api = FakeApi()
            setattr(api, collection, [])
            with self.assertRaises(seed.SeedError):
                self.run_seed(api, apply=True)
            self.assertEqual(api.posts, 0)

    def test_inactive_services_are_not_used(self):
        api = FakeApi()
        api.services[0]["status"] = "INACTIVE"
        with self.assertRaises(seed.SeedError):
            self.run_seed(api)

    def test_wrong_business_rejected(self):
        with self.assertRaisesRegex(seed.SeedError, "otro negocio"):
            seed.seed(FakeApi(), 2, now=NOW)

    def test_cancelled_appointment_still_blocks_staff_and_customer(self):
        api = FakeApi()
        api.rows.append({"id": 700, "staff_member_id": 1, "customer_id": 1,
                         "starts_at": "2029-10-23T09:00:00", "ends_at": "2029-10-23T10:00:00",
                         "status": "CANCELLED"})
        plan = self.run_seed(api, count=6)
        for row in plan:
            if row["startsAt"] < "2029-10-23T10:00:00":
                self.assertNotEqual(row["staffMemberId"], 1)
                self.assertNotEqual(row["customerId"], 1)

    def test_today_is_business_date_not_machine_date(self):
        api = FakeApi()
        original = api.request
        api.request = lambda path, *a, **kw: {"id": 1, "timezone": "Pacific/Honolulu"} if path == "/business" else original(path, *a, **kw)
        with redirect_stdout(io.StringIO()):
            plan = seed.seed(api, 1, 3, now=datetime(2029, 10, 23, 1, tzinfo=timezone.utc))
        self.assertEqual(min(p["startsAt"][:10] for p in plan), "2029-10-22")


    def test_after_close_skips_weekend_and_selects_next_three_available_days(self):
        api = FakeApi()
        late = NOW.replace(hour=23)
        api.closed = {(NOW + timedelta(days=i)).date() for i in (1, 2)}
        with redirect_stdout(io.StringIO()):
            rows = seed.seed(api, 1, now=late)
        expected = {str((NOW + timedelta(days=i)).date()) for i in (3, 4, 5)}
        self.assertEqual({row["startsAt"][:10] for row in rows}, expected)
        self.assertEqual(len(rows), 106)
        self.assertEqual(api.posts, 0)

    def test_calendar_mode_keeps_strict_range_and_fails_without_writes(self):
        api = FakeApi()
        api.closed = {(NOW + timedelta(days=i)).date() for i in (1, 2)}
        with redirect_stdout(io.StringIO()), self.assertRaisesRegex(seed.SeedError, "Capacidad encontrada: 0/106"):
            seed.seed(api, 1, now=NOW.replace(hour=23), apply=True, calendar_days=True)
        self.assertEqual(api.posts, 0)

    def test_full_existing_day_is_skipped_and_batch_remains_idempotent(self):
        api = FakeApi()
        for member in (1, 2):
            api.rows.append({"id": 900+member, "staff_member_id": member, "customer_id": 1,
                             "starts_at": "2029-10-23T00:00:00", "ends_at": "2029-10-24T00:00:00",
                             "status": "CANCELLED"})
        first = self.run_seed(api, count=106, apply=True)
        self.assertEqual(min(row["startsAt"][:10] for row in first), "2029-10-24")
        self.run_seed(api, count=106, apply=True)
        self.assertEqual(api.posts, 106)

    def test_loopback_only_and_credentials_required(self):
        for url in ("https://example.com", "http://localhost.evil", "http://user:password@localhost", "file:///tmp/x"):
            with self.assertRaises(seed.SeedError):
                seed.Api(url, cookie="session=test")
        with self.assertRaises(seed.SeedError):
            seed.Api("http://localhost:8080")

    def test_reads_all_pages_and_rejects_inconsistent_results(self):
        api = seed.Api("http://localhost:8080", cookie="session=test")
        api.request = lambda path, params: {"data": [{"id": params["page"]+1}],
                                            "page": {"number": params["page"], "total_elements": 2, "total_pages": 2}}
        self.assertEqual([r["id"] for r in api.pages("/customers")], [1, 2])
        api.request = lambda path, params: {"data": [{"id": 1}], "page": {"number": 0, "total_elements": 2, "total_pages": 1}}
        with self.assertRaisesRegex(seed.SeedError, "incompleto"):
            api.pages("/customers")

    def test_http_write_uses_cookie_and_actual_camel_case_contract(self):
        api = seed.Api("http://localhost:8080", cookie="turnero_session=secret")
        api.opener.open = Mock(return_value=io.BytesIO(b'{"id":42}'))
        body = self.run_seed(FakeApi(), count=1)[0]
        self.assertEqual(api.request("/appointments", body=body), {"id": 42})
        request = api.opener.open.call_args.args[0]
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Cookie"), "turnero_session=secret")
        payload = seed.json.loads(request.data)
        self.assertEqual(payload["durationMinutes"], 30)
        self.assertEqual(payload["source"], "ADMIN")
        self.assertNotIn("duration_minutes", payload)

    def test_http_auth_error_never_echoes_cookie_or_server_body(self):
        api = seed.Api("http://localhost:8080", cookie="turnero_session=secret")
        api.opener.open = Mock(side_effect=HTTPError("http://localhost", 401, "Unauthorized", {}, io.BytesIO(b'secret')))
        with self.assertRaises(seed.SeedError) as error:
            api.request("/business")
        self.assertIn("401", str(error.exception))
        self.assertNotIn("secret", str(error.exception))
        self.assertIsNone(seed.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.com"))

    def test_changed_reference_stops_before_first_write(self):
        api = FakeApi()
        original = api.request
        api.request = lambda path, *a, **kw: {"status": "INACTIVE"} if path == "/staff-members/1" else original(path, *a, **kw)
        with self.assertRaisesRegex(seed.SeedError, "estado del profesional"):
            self.run_seed(api, count=3, apply=True)
        self.assertEqual(api.posts, 0)

    def test_preceding_day_appointment_blocks_customer_today(self):
        api = FakeApi()
        api.rows.append({"id": 900, "staff_member_id": 1, "customer_id": 1,
                         "starts_at": "2029-10-22T23:30:00", "ends_at": "2029-10-23T10:00:00",
                         "status": "COMPLETED"})
        plan = self.run_seed(api, count=3)
        for row in plan:
            if row["startsAt"] < "2029-10-23T10:00:00":
                self.assertNotEqual(row["staffMemberId"], 1)
                self.assertNotEqual(row["customerId"], 1)


if __name__ == "__main__":
    unittest.main()
