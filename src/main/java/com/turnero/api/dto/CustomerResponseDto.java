package com.turnero.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponseDto {

    @Schema(description = "Customer ID", example = "1")
    private Long id;

    @Schema(description = "The customer's name", example = "Jorge Silva")
    private String name;

    @Schema(description = "The customer's email address", example = "jorge.silva@mail.com")
    private String email;

    @Schema(description = "The customer's phone number", example = "11912345678")
    private String phone;

    @Schema(description = "Customer creation date and time", example = "2024-12-01T10:00:00")
    private LocalDateTime createdAt;
}
