package com.turnero.api.dto;

import java.util.List;

public record CustomerAppointmentPageResponseDto(List<CustomerAppointmentReadDto> data,
                                                 CustomerPageResponseDto.PageInfo page) {}
