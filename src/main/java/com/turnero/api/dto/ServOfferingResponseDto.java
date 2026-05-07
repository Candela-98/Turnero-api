package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServOfferingResponseDto {

    private Long id;
    private String name;
    private int durationMinutes;
    private double price;
}
