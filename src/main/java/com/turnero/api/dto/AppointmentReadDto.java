package com.turnero.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AppointmentSource;

/** Read-only projection. Never serializes customer booking block reasons. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AppointmentReadDto(Long id, Long customerId, Long serviceOfferingId, Long staffMemberId,
        OffsetDateTime startsAt, OffsetDateTime endsAt, int durationMinutes, int priceCents,
        AppointmentStatus status, AppointmentSource source, String customerNotes, String internalNotes,
        String cancellationReason, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        CustomerReference customer, ServiceReference serviceOffering, StaffReference staffMember) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CustomerReference(Long id, boolean available, String name, String email, String phoneNumber, String status) {}
    public record ServiceReference(Long id, boolean available, String name, String category, String status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StaffReference(Long id, boolean available, String name, String roleLabel, String avatarUrl, String status, OffsetDateTime deletedAt) {}
}
