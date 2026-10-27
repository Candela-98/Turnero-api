package com.turnero.api.dto;
import java.util.List;
public record AppointmentPageResponseDto(List<AppointmentReadDto> data, CustomerPageResponseDto.PageInfo page) {}
