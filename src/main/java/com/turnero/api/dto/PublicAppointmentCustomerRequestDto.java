package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PublicAppointmentCustomerRequestDto {

    @NotBlank(message = "Customer name is required")
    private String name;

    @Email(message = "Customer email must be valid")
    @NotBlank(message = "Customer email is required")
    private String email;

    @NotBlank(message = "Customer phone number is required")
    private String phoneNumber;
}
