package com.turnero.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceOfferingSortTest {
    @Test
    void missingParameterUsesDefaultNameOrderWithStableIdTieBreaker() {
        Sort expected = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
        assertEquals(expected, ServiceOfferingSort.fromParameter(null));
        assertEquals(expected, ServiceOfferingSort.fromParameter(ServiceOfferingSort.DEFAULT_PARAMETER));
    }

    @ParameterizedTest
    @CsvSource({
            "name,name", "category,category", "duration_minutes,durationMinutes",
            "price_cents,priceCents", "status,status"
    })
    void allowedPublicFieldsResolveToEntityPropertiesInBothDirections(String publicField, String property) {
        assertEquals(Sort.by(Sort.Order.asc(property), Sort.Order.asc("id")),
                ServiceOfferingSort.fromParameter(publicField + ",asc"));
        assertEquals(Sort.by(Sort.Order.desc(property), Sort.Order.asc("id")),
                ServiceOfferingSort.fromParameter(publicField + ",desc"));
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {
            "name", "name,", ",asc", "name,asc,id", "businessId,asc", "id,asc",
            "durationMinutes,asc", "priceCents,desc", "name,sideways", "name,ASC", "name, desc"
    })
    void malformedOrUnlistedSortIsRejected(String parameter) {
        assertThrows(IllegalArgumentException.class, () -> ServiceOfferingSort.fromParameter(parameter));
    }
}
