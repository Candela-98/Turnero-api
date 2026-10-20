package com.turnero.api.service;

import org.springframework.data.domain.Sort;

/** Allowlisted public ordering with stable pages for customers sharing a name. */
public final class CustomerSort {
    public static final String DEFAULT_PARAMETER = "name,asc";

    private CustomerSort() {}

    public static Sort fromParameter(String parameter) {
        var direction = switch (parameter == null ? DEFAULT_PARAMETER : parameter) {
            case "name,asc" -> Sort.Direction.ASC;
            case "name,desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("Invalid customer sort parameter");
        };
        return Sort.by(new Sort.Order(direction, "name"), Sort.Order.asc("id"));
    }
}
