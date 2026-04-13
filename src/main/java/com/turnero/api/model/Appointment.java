package com.turnero.api.model;

import jakarta.persistence.*;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import lombok.*;
import org.springframework.stereotype.Service;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

