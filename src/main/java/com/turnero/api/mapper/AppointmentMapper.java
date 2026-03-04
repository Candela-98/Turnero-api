package com.turnero.api.mapper;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.model.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AppointmentMapper {

    public Appointment toEntity(AppointmentRequestDto dto) {
        Appointment appointment = new Appointment();
        appointment.setCustomerId(dto.getCustomerId());
        appointment.setServiceId(dto.getServiceId());
        appointment.setStaffMemberId(dto.getStaffMemberId());
        appointment.setDateTime(dto.getDateTime());
        appointment.setDurationMinutes(dto.getDurationMinutes());
        appointment.setStatus(dto.getStatus());
        appointment.setNotes(dto.getNotes());
        appointment.setCreatedAt(LocalDateTime.now());
        appointment.setUpdateAt(LocalDateTime.now());

        return appointment;
    }
}
