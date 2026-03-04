package com.turnero.api.dto;

import com.turnero.api.model.AppointmentStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class AppointmentRequestDto {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotNull(message = "Staff member ID is required")
    private Long staffMemberId;

    @NotNull(message = "Appointment date and time is required")
    @Future(message = "The date and time must be in the future")
    private LocalDateTime dateTime;

    @Min(value = 1, message = "Minimum duration is 1 minute")
    private int durationMinutes;

    @NotNull(message = "Appointment status is required")
    private AppointmentStatus status;

    private String notes;

    // Getters y setters
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

    public Long getStaffMemberId() { return staffMemberId; }
    public void setStaffMemberId(Long staffMemberId) { this.staffMemberId = staffMemberId; }

    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
