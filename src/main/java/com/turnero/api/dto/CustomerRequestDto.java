package com.turnero.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerRequestDto {

    private Long customerId;

    @NotNull(message = "The customer's name is required.")
    private String nameCustomer;

    @NotNull(message = "The customer's email address is required.")
    private String email;

    @NotNull(message = "The customer's phone number is mandatory.")
    private String phoneCustomer;

    @NotNull(message = "The customer creation date is mandatory.")
    private LocalDateTime creationDate;

}
