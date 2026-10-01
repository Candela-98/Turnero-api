package com.turnero.api.service;

import org.springframework.data.domain.Sort;

import java.util.Map;

/** Resolves the public ordering contract into entity properties accepted by Spring Data. */
public final class ServiceOfferingSort {
    public static final String DEFAULT_PARAMETER = "name,asc";

    private static final Map<String, String> PROPERTIES = Map.of(
            "name", "name",
            "category", "category",
            "duration_minutes", "durationMinutes",
            "price_cents", "priceCents",
            "status", "status"
    );

    private ServiceOfferingSort() {
    }

    public static Sort fromParameter(String parameter) {
        String[] parts = (parameter == null ? DEFAULT_PARAMETER : parameter).split(",", -1);
        if (parts.length != 2 || !PROPERTIES.containsKey(parts[0])) {
            throw new IllegalArgumentException("Invalid service offering sort parameter");
        }
        Sort.Direction direction = switch (parts[1]) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("Invalid service offering sort parameter");
        };
        return Sort.by(new Sort.Order(direction, PROPERTIES.get(parts[0])), Sort.Order.asc("id"));
    }
}
