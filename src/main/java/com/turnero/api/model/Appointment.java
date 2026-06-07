package com.turnero.api.model;

import com.turnero.api.model.enums.AppointmentSource;
import com.turnero.api.model.enums.AppointmentStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;

    private Long customerId;

    private Long serviceOfferingId;

    private Long staffMemberId;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    private int durationMinutes;

    private int priceCents;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    private AppointmentSource source;

    @Column(columnDefinition = "TEXT")
    private String customerNotes;

    @Column(columnDefinition = "TEXT")
    private String internalNotes;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}

