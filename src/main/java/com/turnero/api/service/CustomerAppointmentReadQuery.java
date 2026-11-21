package com.turnero.api.service;

import org.springframework.util.MultiValueMap;

import java.util.Set;

public record CustomerAppointmentReadQuery(int page, int size, boolean descending) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "sort");

    public static CustomerAppointmentReadQuery parse(MultiValueMap<String, String> params) {
        if (!PARAMETERS.containsAll(params.keySet())) throw new IllegalArgumentException("Invalid query parameter");
        String page = single(params, "page", "0");
        String size = single(params, "size", "50");
        String sort = single(params, "sort", "starts_at,desc");
        int pageNumber = Integer.parseInt(page);
        int pageSize = Integer.parseInt(size);
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page >= 0 and size 1–100 required");
        }
        if (!sort.equals("starts_at,asc") && !sort.equals("starts_at,desc")) {
            throw new IllegalArgumentException("Invalid sort");
        }
        return new CustomerAppointmentReadQuery(pageNumber, pageSize, sort.endsWith("desc"));
    }

    private static String single(MultiValueMap<String, String> params, String key, String fallback) {
        var values = params.get(key);
        if (values == null) return fallback;
        if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return values.getFirst();
    }
}
