package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponseDto {

    private Long customerId;
    private String nameCustomer;
    private String email;
    private String phoneCustomer;
    private LocalDateTime createdIn;
}
