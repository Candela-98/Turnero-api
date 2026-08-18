package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BusinessResponseDto {
    private Long id;
    private String name;
    private String slug;
    private String industry;
    private String email;
    private String phone;
    private String address;
    private String timezone;
    private BusinessStatus status;
    private BusinessOnboardingStatus onboardingStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
