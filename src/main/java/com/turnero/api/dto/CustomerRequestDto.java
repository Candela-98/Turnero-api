package com.turnero.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class CustomerRequestDto {

    @Schema(description = "The customer's name", example = "Jorge Silva")
    @NotBlank(message = "The customer's name is required.")
    @Size(max = 100, message = "The customer's name must have at most 100 characters.")
    private String name;

    @Schema(description = "The customer's email address", example = "jorge.silva@mail.com")
    @Email(message = "The customer's email address must be valid.")
    @Size(max = 150, message = "The customer's email address must have at most 150 characters.")
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    @Schema(description = "The customer's phone number", example = "11912345678")
    @NotBlank(message = "The customer's phone number is mandatory.")
    @Size(max = 30, message = "The customer's phone number must have at most 30 characters.")
    @JsonAlias("phone")
    private String phoneNumber;

    @Schema(description = "Internal administrative notes about the customer")
    @Size(max = 2000, message = "The customer's internal notes must have at most 2000 characters.")
    private String internalNotes;
}
