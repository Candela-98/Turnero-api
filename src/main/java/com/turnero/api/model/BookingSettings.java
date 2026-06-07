package com.turnero.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "booking_settings")
public class BookingSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long businessId;

    private boolean publicBookingEnabled;
    private boolean requiresCustomerLogin;

    private int bookingWindowDays;
    private int minNoticeHours;
    private int cancellationNoticeHours;
    private int slotIntervalMinutes;

    private boolean manualConfirmationEnabled;
    private boolean whatsappRemindersEnabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
