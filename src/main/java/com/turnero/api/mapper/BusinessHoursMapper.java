package com.turnero.api.mapper;

import com.turnero.api.dto.BusinessHoursResponseDto;
import com.turnero.api.model.BusinessHours;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessHoursMapper {
    public BusinessHoursResponseDto toResponseDto(BusinessHours entity) {
        return BusinessHoursResponseDto.builder()
                .id(entity.getId())
                .dayOfWeek(entity.getDayOfWeek())
                .opensAt(entity.getOpensAt())
                .closesAt(entity.getClosesAt())
                .isClosed(entity.isClosed())
                .build();
    }

    public List<BusinessHoursResponseDto> toResponseDtoList(List<BusinessHours> entities) {
        return entities.stream().map(this::toResponseDto).toList();
    }
}
