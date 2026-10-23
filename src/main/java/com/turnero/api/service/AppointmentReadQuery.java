package com.turnero.api.service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.util.MultiValueMap;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AppointmentSource;

/** Parses and validates collection parameters independently of the HTTP controller. */
public record AppointmentReadQuery(LocalDateTime from, LocalDateTime to, List<String> statuses,
        Long staffId, Long customerId, Long serviceId, String source, int page, int size, boolean descending, List<BusinessLocalTimeRange.Window> windows) {
    public static AppointmentReadQuery parse(MultiValueMap<String, String> params, ZoneId zone, Clock clock) {
        try {
            String date = single(params, "date"), from = single(params, "from"), to = single(params, "to");
            LocalDateTime start, end;
            List<BusinessLocalTimeRange.Window> windows;
            if (date != null && (from != null || to != null)) throw new IllegalArgumentException("Use date or from/to, exclusively");
            if ((from == null) != (to == null)) throw new IllegalArgumentException("from and to are required together");
            if (from != null) {
                var first = OffsetDateTime.parse(from).atZoneSameInstant(zone);
                var last = OffsetDateTime.parse(to).atZoneSameInstant(zone);
                if (!last.toInstant().isAfter(first.toInstant()) || last.toInstant().isAfter(first.plusDays(31).toInstant()))
                    throw new IllegalArgumentException("Range must be positive and at most 31 business calendar days");
                start = first.toLocalDateTime(); end = last.toLocalDateTime();
                windows = BusinessLocalTimeRange.windows(first.toInstant(), last.toInstant(), zone);
            } else {
                var day = date == null ? LocalDate.now(clock.withZone(zone)) : LocalDate.parse(date);
                start = day.atStartOfDay(); end = day.plusDays(1).atStartOfDay();
                windows = List.of(new BusinessLocalTimeRange.Window(start, end));
            }
            var states = new LinkedHashSet<String>();
            for (String raw : params.getOrDefault("status", List.of())) {
                for (String state : raw.split(",", -1)) states.add(AppointmentStatus.valueOf(state.trim()).name());
            }
            var origin = single(params, "source");
            if (origin != null) origin = AppointmentSource.valueOf(origin).name();
            int page = integer(params, "page", 0), size = integer(params, "size", 50);
            if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page >= 0 and size 1–100 required");
            String sort = Optional.ofNullable(single(params, "sort")).orElse("starts_at,asc");
            if (!sort.equals("starts_at,asc") && !sort.equals("starts_at,desc")) throw new IllegalArgumentException("Invalid sort");
            return new AppointmentReadQuery(start, end, List.copyOf(states), positive(params, "staff_member_id"),
                positive(params, "customer_id"), positive(params, "service_offering_id"), origin, page, size, sort.endsWith("desc"), windows);
        } catch (DateTimeParseException ex) { throw new IllegalArgumentException("Invalid ISO date or offset timestamp", ex); }
    }
    private static String single(MultiValueMap<String, String> params, String key) {
        var values = params.get(key);
        if (values == null) return null;
        if (values.size() != 1 || values.getFirst().isBlank()) throw new IllegalArgumentException("Invalid " + key);
        return values.getFirst();
    }
    private static int integer(MultiValueMap<String, String> params, String key, int fallback) {
        String value = single(params, key); return value == null ? fallback : Integer.parseInt(value);
    }
    private static Long positive(MultiValueMap<String, String> params, String key) {
        String value = single(params, key);
        if (value == null) return null;
        long id = Long.parseLong(value);
        if (id <= 0) throw new IllegalArgumentException(key + " must be positive");
        return id;
    }
}
