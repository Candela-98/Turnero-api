package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponseDto {

    @Schema(description = "Customer ID", example = "1")
    private Long id;

    @Schema(description = "The customer's name", example = "Jorge Silva")
    private String name;

    @Schema(description = "The customer's email address", example = "jorge.silva@mail.com")
    private String email;

    @Schema(description = "The customer's phone number", example = "11912345678")
    private String phoneNumber;

    @Schema(description = "The customer's status", example = "ACTIVE")
    private CustomerStatus status;

    @Schema(description = "Internal administrative notes about the customer", example = "Prefers a low fade.")
    private String internalNotes;

    @Schema(description = "Customer creation date and time", example = "2024-12-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Date and time of the last customer update", example = "2024-12-15T12:00:00")
    private LocalDateTime updatedAt;
}
