package com.turnero.api.service;

import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
<<<<<<< HEAD
import com.turnero.api.dto.AppointmentUpdateRequestDto;
=======
>>>>>>> develop
import com.turnero.api.model.Appointment;
import java.util.List;

public interface AppointmentService {

    AppointmentResponseDto saveAppointment(AppointmentRequestDto request);

    List<Appointment> findAllAppointments();

    Appointment findAppointment(Long id);

    AppointmentResponseDto updateAppointment(Long id, AppointmentUpdateRequestDto request);

    void deleteAppointment(Long id);


}
