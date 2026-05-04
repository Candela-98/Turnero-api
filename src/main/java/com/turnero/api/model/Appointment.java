package com.turnero.api.model;

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

    private Long customerId;

    private Long serviceId;

    private Long staffMemberId;

    private LocalDateTime dateTime;

    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updateAt;

}

