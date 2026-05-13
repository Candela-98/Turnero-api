package com.turnero.api.service;

import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.AppointmentStatus;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ServOfferingRepository servOfferingRepository;

    @Mock
    private StaffMemberRepository staffMemberRepository;

    @Test
    void saveAppointment() {
        Appointment appointment = new Appointment();
        appointment.setCustomerId(1L);
        appointment.setServiceId(2L);
        appointment.setStaffMemberId(3L);

        when(customerRepository.existsById(1L)).thenReturn(true);
        when(servOfferingRepository.existsById(2L)).thenReturn(true);
        when(staffMemberRepository.existsById(3L)).thenReturn(true);

        appointmentService.saveAppointment(appointment);

        assertNotNull(appointment.getCreatedAt());
        assertNotNull(appointment.getUpdatedAt());

        verify(appointmentRepository, times(1)).save(appointment);
    }

    @Test
    void saveAppointment_whenRepositoryFails_throwsException() {
        Appointment appointment = new Appointment();
        appointment.setCustomerId(1L);
        appointment.setServiceId(2L);
        appointment.setStaffMemberId(3L);

        when(customerRepository.existsById(1L)).thenReturn(true);
        when(servOfferingRepository.existsById(2L)).thenReturn(true);
        when(staffMemberRepository.existsById(3L)).thenReturn(true);

        doThrow(new RuntimeException("Error saving")).when(appointmentRepository).save(appointment);
        assertThrows(RuntimeException.class, () -> appointmentService.saveAppointment(appointment));
    }

    @Test
    void saveAppointment_whenCustomerNotExist_shouldthrowsException() {
        Appointment appointment = new Appointment();
        appointment.setCustomerId(99L);
        appointment.setServiceId(2L);
        appointment.setStaffMemberId(3L);

        when(customerRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.saveAppointment(appointment)
        );

        assertEquals("Customer not found.", exception.getMessage());
    }

    @Test
    void saveAppointment_whenServiceNotExist_shouldthrowsException() {
        Appointment appointment = new Appointment();
        appointment.setCustomerId(1L);
        appointment.setServiceId(99L);
        appointment.setStaffMemberId(3L);

        when(customerRepository.existsById(1L)).thenReturn(true);
        when(servOfferingRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.saveAppointment(appointment)
        );

        assertEquals("Service offering not found.", exception.getMessage());
        verify(appointmentRepository, never()).save(any());
    }

     @Test
    void saveAppointment_whenStaffMemberNotExist_shouldthrowsException() {
         Appointment appointment = new Appointment();
         appointment.setCustomerId(1L);
         appointment.setServiceId(2L);
         appointment.setStaffMemberId(99L);

         when(customerRepository.existsById(1L)).thenReturn(true);
         when(servOfferingRepository.existsById(2L)).thenReturn(true);
         when(staffMemberRepository.existsById(99L)).thenReturn(false);

         ResourceNotFoundException exception = assertThrows(
                 ResourceNotFoundException.class,
                 () -> appointmentService.saveAppointment(appointment)
         );

         assertEquals("Staff member not found.", exception.getMessage());
         verify(appointmentRepository, never()).save(any());
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

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.findAppointment(id)
        );

        assertEquals("Appointment not found with ID: 99", exception.getMessage());
    }

    @Test
    void updateAppointment_shouldUpdateAndSave() {
        Long id = 1L;
        LocalDateTime originalCreatedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime originalUpdatedAt = LocalDateTime.now().minusDays(1);

        Appointment current = new Appointment();
        current.setId(id);
        current.setCreatedAt(originalCreatedAt);
        current.setUpdatedAt(originalUpdatedAt);

        Appointment updateAppointment = new Appointment();
        updateAppointment.setCustomerId(10L);
        updateAppointment.setServiceId(20L);
        updateAppointment.setStaffMemberId(30L);
        updateAppointment.setDateTime(java.time.LocalDateTime.of(2026, 2, 15, 10, 0));
        updateAppointment.setDurationMinutes(45);
        updateAppointment.setStatus(AppointmentStatus.CONFIRMED);
        updateAppointment.setNotes("Notes");

        when(appointmentRepository.findById(id)).thenReturn(Optional.of(current));
        when(customerRepository.existsById(10L)).thenReturn(true);
        when(servOfferingRepository.existsById(20L)).thenReturn(true);
        when(staffMemberRepository.existsById(30L)).thenReturn(true);

        appointmentService.updateAppointment(updateAppointment, id);

        verify(appointmentRepository, times(1)).save(current);

        assertEquals(10L, current.getCustomerId());
        assertEquals(20L, current.getServiceId());
        assertEquals(30L, current.getStaffMemberId());
        assertEquals(java.time.LocalDateTime.of(2026, 2, 15, 10, 0), current.getDateTime());
        assertEquals(45, current.getDurationMinutes());
        assertEquals(AppointmentStatus.CONFIRMED, current.getStatus());
        assertEquals("Notes", current.getNotes());

        assertEquals(originalCreatedAt, current.getCreatedAt());
        assertNotNull(current.getUpdatedAt());
        assertTrue(current.getUpdatedAt().isAfter(originalUpdatedAt));
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

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.deleteAppointment(id)
        );

        assertEquals("Appointment not found with ID: 99", exception.getMessage());

        verify(appointmentRepository, never()).deleteById(anyLong());
    }
}

