package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CustomerUpdateRequestDto {

    @Schema(description = "The customer's name", example = "Santiago Moreno")
    @Size(min = 1, max = 100, message = "The customer's name must have between 1 and 100 characters.")
    @Pattern(regexp = "(?s).*\\S.*", message = "The customer's name cannot be blank.")
    private String name;

    @Schema(description = "The customer's email address", example = "santiago.moreno@mail.demo")
    @Email(message = "The customer's email address must be valid.")
    @Size(max = 150, message = "The customer's email address must have at most 150 characters.")
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    @Schema(description = "The customer's phone number", example = "+54 11 5555-5555")
    @Size(min = 1, max = 30, message = "The customer's phone number must have between 1 and 30 characters.")
    @Pattern(regexp = "(?s).*\\S.*", message = "The customer's phone number cannot be blank.")
    private String phoneNumber;

    @Schema(description = "Internal administrative notes about the customer", example = "Prefers a low fade.")
    @Size(max = 2000, message = "The customer's internal notes must have at most 2000 characters.")
    private String internalNotes;

    @Schema(description = "The customer's status", example = "ACTIVE")
    private CustomerStatus status;

    private Boolean publicBookingBlocked;

    @Size(max = 500, message = "The blocking reason must have at most 500 characters.")
    private String publicBookingBlockReason;
}
