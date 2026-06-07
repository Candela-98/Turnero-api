package com.turnero.api.mapper;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;


@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "businessId", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    Appointment toEntity(AppointmentRequestDto dto);

    AppointmentResponseDto toResponseDto(Appointment appointment);

    List<AppointmentResponseDto> toResponseDtoList(List<Appointment> appointments);
}
