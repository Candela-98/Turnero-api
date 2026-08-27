package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BusinessHoursListResponseDto {
    private List<BusinessHoursResponseDto> data;
}
