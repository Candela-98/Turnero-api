package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CustomerUpdateRequestDto {

    @Schema(description = "The customer's name", example = "Santiago Moreno")
    @Size(min = 1, max = 100, message = "The customer's name must have between 1 and 100 characters.")
    private String name;

    @Schema(description = "The customer's email address", example = "santiago.moreno@mail.demo")
    @Email(message = "The customer's email address must be valid.")
    @Size(max = 150, message = "The customer's email address must have at most 150 characters.")
    private String email;

    @Schema(description = "The customer's phone number", example = "+54 11 5555-5555")
    @Size(max = 30, message = "The customer's phone number must have at most 30 characters.")
    private String phoneNumber;

    @Schema(description = "Internal administrative notes about the customer", example = "Prefers a low fade.")
    @Size(max = 2000, message = "The customer's internal notes must have at most 2000 characters.")
    private String internalNotes;

    @Schema(description = "The customer's status", example = "ACTIVE")
    private CustomerStatus status;
}
