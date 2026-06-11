package com.turnero.api.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DevCurrentBusinessContextTest {
    @Test
    void getCurrentBusinessId_shouldReturnSeedBusinessId() {
        DevCurrentBusinessContext context = new DevCurrentBusinessContext();

        Long businessId = context.getCurrentBusinessId();

        assertEquals(1L, businessId);
    }
}
