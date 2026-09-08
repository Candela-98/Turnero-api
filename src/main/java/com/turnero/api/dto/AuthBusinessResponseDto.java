package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turnero.api.model.enums.BusinessOnboardingStatus;

public record AuthBusinessResponseDto(
        Long id,
        String name,
        String slug,
        @JsonProperty("onboarding_status") BusinessOnboardingStatus onboardingStatus
) {
}
