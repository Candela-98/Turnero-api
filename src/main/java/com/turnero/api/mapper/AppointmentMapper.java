package com.turnero.api.mapper;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;


@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "updateAt", expression = "java(java.time.LocalDateTime.now())")
    Appointment toEntity(AppointmentRequestDto dto);

    AppointmentResponseDto toResponseDto(Appointment appointment);

    List<AppointmentResponseDto> toResponseDtoList(List<Appointment> appointments);
}
