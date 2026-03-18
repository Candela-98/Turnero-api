package com.turnero.api.service;

import com.turnero.api.model.Appointment;
import com.turnero.api.model.AppointmentStatus;
import com.turnero.api.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    @Test
    void saveAppointment() {
        Appointment appointment = new Appointment();
        appointmentService.saveAppointment(appointment);
        verify(appointmentRepository, times(1)).save(appointment);
    }

    @Test
    void findAllAppointments() {
        Appointment appointment1 = new Appointment();
        Appointment appointment2 = new Appointment();
        when(appointmentRepository.findAll()).thenReturn(Arrays.asList(appointment1, appointment2));
        List<Appointment> appointments = appointmentService.findAllAppointments();
        assertEquals(2, appointments.size());
        assertTrue(appointments.contains(appointment1));
        assertTrue(appointments.contains(appointment2));
    }

    @Test
    void saveAppointment_cuandoRepositoryFalla_lanzaExcepcion() {
        Appointment appointment = new Appointment();
        doThrow(new RuntimeException("Error saving")).when(appointmentRepository).save(appointment);
        assertThrows(RuntimeException.class, () -> appointmentService.saveAppointment(appointment));
    }

    @Test
    void findAppointment() {
        Long id = 1L;
        Appointment appointment = new Appointment();
        when(appointmentRepository.findById(id)).thenReturn(Optional.of(appointment));

        Appointment result = appointmentService.findAppointment(id);

        assertNotNull(result);
        assertEquals(appointment, result);
        verify(appointmentRepository, times(1)).findById(id);
    }

    @Test
    void findAppointment_whenNotExist_throwsException() {
        Long id = 99L;
        when(appointmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> appointmentService.findAppointment(id));
    }

    @Test
    void updateAppointment_shouldUpdateAndSave() {
        Long id = 1L;

        Appointment current = new Appointment();
        current.setId(id);

        Appointment updateAppointment = new Appointment();
        updateAppointment.setCustomerId(10L);
        updateAppointment.setServiceId(20L);
        updateAppointment.setStaffMemberId(30L);
        updateAppointment.setDateTime(java.time.LocalDateTime.of(2026, 2, 15, 10, 0));
        updateAppointment.setDurationMinutes(45);
        updateAppointment.setStatus(AppointmentStatus.CONFIRMED);
        updateAppointment.setNotes("Notes");

        when(appointmentRepository.findById(id)).thenReturn(Optional.of(current));

        appointmentService.updateAppointment(updateAppointment, id);

        verify(appointmentRepository, times(1)).save(current);

        assertEquals(10L, current.getCustomerId());
        assertEquals(20L, current.getServiceId());
        assertEquals(30L, current.getStaffMemberId());
        assertEquals(java.time.LocalDateTime.of(2026, 2, 15, 10, 0), current.getDateTime());
        assertEquals(45, current.getDurationMinutes());
        assertEquals(AppointmentStatus.CONFIRMED, current.getStatus());
        assertEquals("Notes", current.getNotes());
    }

    @Test
    void deleteAppointment_whenExists_shouldDelete() {
        Long id = 1L;
        when(appointmentRepository.existsById(id)).thenReturn(true);

        appointmentService.deleteAppointment(id);

        verify(appointmentRepository, times(1)).deleteById(id);
    }

    @Test
    void deleteAppointment_whenDoesNotExist_shouldNotDelete() {
        Long id = 99L;
        when(appointmentRepository.existsById(id)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> appointmentService.deleteAppointment(id));

        verify(appointmentRepository, never()).deleteById(anyLong());
    }
}

