package com.turnero.api.model;

import com.turnero.api.model.enums.CustomerStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;

    private Long userId;

    private String name;

    private String email;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    @Column(columnDefinition = "TEXT")
    private String internalNotes;

    @Builder.Default
    @Column(nullable = false)
    private boolean publicBookingBlocked = false;

    @Column(length = 500)
    private String publicBookingBlockReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
