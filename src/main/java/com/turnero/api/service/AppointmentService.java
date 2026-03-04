package com.turnero.api.service;

import com.turnero.api.model.Appointment;
import java.util.List;

public interface AppointmentService {

    void saveAppointment(Appointment appointment);
    List<Appointment> findAllAppointments();

    Appointment findAppointment(Long id);

    void updateAppointment(Appointment appointment, Long id);

    void deleteAppointment(Long id);


}
