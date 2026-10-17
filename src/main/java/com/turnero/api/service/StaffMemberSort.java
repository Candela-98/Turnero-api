package com.turnero.api.service;

import org.springframework.data.domain.Sort;

/** Public list ordering, with stable pagination for duplicate names. */
public final class StaffMemberSort {
    public static final String DEFAULT_PARAMETER = "name,asc";
    private StaffMemberSort() {}

    public static Sort fromParameter(String parameter) {
        String value = parameter == null ? DEFAULT_PARAMETER : parameter;
        Sort.Direction direction = switch (value) {
            case "name,asc" -> Sort.Direction.ASC;
            case "name,desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("Invalid staff member sort parameter");
        };
        return Sort.by(new Sort.Order(direction, "name"), Sort.Order.asc("id"));
    }
}
