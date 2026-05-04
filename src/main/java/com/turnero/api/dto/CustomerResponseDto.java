package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponseDto {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private LocalDateTime createdIn;
}
