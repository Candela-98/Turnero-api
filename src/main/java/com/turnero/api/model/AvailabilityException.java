package com.turnero.api.model;

import com.turnero.api.model.enums.AvailabilityExceptionsType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "availability_exceptions")
public class AvailabilityException {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;
    private Long staffMemberId;

    private LocalDate date;

    private LocalTime startsAt;
    private LocalTime endsAt;

    @Enumerated(EnumType.STRING)
    private AvailabilityExceptionsType type;

    private String reason;
}
