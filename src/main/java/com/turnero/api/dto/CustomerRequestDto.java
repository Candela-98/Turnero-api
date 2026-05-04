package com.turnero.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerRequestDto {

    @NotBlank(message = "The customer's name is required.")
    @Size(max = 100, message = "The customer's name must have at most 100 characters.")
    private String name;

    @NotBlank(message = "The customer's email address is required.")
    @Email(message = "The customer's email address must be valid.")
    @Size(max = 150, message = "The customer's email address must have at most 150 characters.")
    private String email;

    @NotBlank(message = "The customer's phone number is mandatory.")
    @Size(max = 30, message = "The customer's phone number must have at most 30 characters.")
    private String phone;
}
