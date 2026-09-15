package com.turnero.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PublicServiceOfferingListResponseDto {

    private List<PublicServiceOfferingResponseDto> services;
}
