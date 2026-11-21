package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.turnero.api.model.enums.AppointmentStatus;

import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CustomerAppointmentReadDto(Long id, NamedReference serviceOffering, NamedReference staffMember,
                                         OffsetDateTime startsAt, OffsetDateTime endsAt, AppointmentStatus status) {
    public record NamedReference(Long id, String name) {}
}
