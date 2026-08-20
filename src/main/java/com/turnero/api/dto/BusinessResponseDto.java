package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BusinessResponseDto {
    @Schema(description = "Business identifier", example = "1")
    private Long id;
    @Schema(description = "Business name", example = "Barber Studio")
    private String name;
    @Schema(description = "Public business slug", example = "barber-studio")
    private String slug;
    @Schema(description = "Business industry", example = "Barber shop")
    private String industry;
    @Schema(description = "Business email", example = "contact@barber-studio.com")
    private String email;
    @Schema(description = "Business phone", example = "+54 11 5555 5555")
    private String phone;
    @Schema(description = "Business address", example = "Av. Siempre Viva 123")
    private String address;
    @Schema(description = "IANA timezone", example = "America/Argentina/Buenos_Aires")
    private String timezone;
    @Schema(description = "Business lifecycle status", example = "ACTIVE")
    private BusinessStatus status;
    @Schema(description = "Onboarding status", example = "PENDING_SETUP")
    private BusinessOnboardingStatus onboardingStatus;
    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
