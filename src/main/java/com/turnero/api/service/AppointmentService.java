package com.turnero.api.service;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.model.Appointment;
import java.util.List;

public interface AppointmentService {

    AppointmentResponseDto saveAppointment(AppointmentRequestDto request);

    List<Appointment> findAllAppointments();

    Appointment findAppointment(Long id);

    void updateAppointment(Appointment appointment, Long id);

    void deleteAppointment(Long id);


}
