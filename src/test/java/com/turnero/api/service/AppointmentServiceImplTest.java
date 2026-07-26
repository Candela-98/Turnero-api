package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.AppointmentCancelRequestDto;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;

import com.turnero.api.dto.AppointmentUpdateRequestDto;

import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.InvalidStateTransitionException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.AppointmentStatus;
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
    private AppointmentMapper appointmentMapper;

    @Mock
    private CurrentBusinessContext currentBusinessContext;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ServOfferingRepository servOfferingRepository;

    @Mock
    private StaffMemberRepository staffMemberRepository;

    private AppointmentRequestDto getRequestDto() {
        return AppointmentRequestDto.builder()
                .customerId(1L)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .customerNotes("Notes")
                .build();
    }


    private AppointmentUpdateRequestDto getUpdateRequestDto(){
        return AppointmentUpdateRequestDto.builder()
                .customerId(1L)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                .customerNotes("Notes")
                .internalNotes("VIP customer")
                .build();
    }

    @Test
    void saveAppointment() {
        Long businessId = 1L;

        AppointmentRequestDto request = getRequestDto();

        Appointment appointment = new Appointment();
        appointment.setId(1L);
        appointment.setCustomerId(1L);
        appointment.setServiceOfferingId(2L);
        appointment.setStaffMemberId(3L);
        appointment.setStartsAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        appointment.setDurationMinutes(30);

        AppointmentResponseDto responseDto = AppointmentResponseDto.builder()
                .id(1L)
                .customerId(1L)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                .durationMinutes(30)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);

        when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
        when(servOfferingRepository.existsByIdAndBusinessId(2L, businessId)).thenReturn(true);
        when(staffMemberRepository.existsByIdAndBusinessId(3L, businessId)).thenReturn(true);
        when(appointmentRepository.findByStaffMemberIdAndBusinessId(3L, businessId)).thenReturn(List.of());
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponseDto(appointment)).thenReturn(responseDto);

        AppointmentResponseDto result = appointmentService.saveAppointment(request);

        assertNotNull(result);
        assertNotNull(appointment.getCreatedAt());
        assertNotNull(appointment.getUpdatedAt());
        assertEquals(businessId, appointment.getBusinessId());

        verify(appointmentRepository, times(1)).save(appointment);
    }

    @Test
    void saveAppointment_whenRepositoryFails_throwsException() {
        Long businessId = 1L;
        AppointmentRequestDto request = getRequestDto();

        Appointment appointment = new Appointment();
        appointment.setCustomerId(1L);
        appointment.setServiceOfferingId(2L);
        appointment.setStaffMemberId(3L);
        appointment.setStartsAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        appointment.setDurationMinutes(30);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);

        when(appointmentMapper.toEntity(request)).thenReturn(appointment);

        when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
        when(servOfferingRepository.existsByIdAndBusinessId(2L, businessId)).thenReturn(true);
        when(staffMemberRepository.existsByIdAndBusinessId(3L, businessId)).thenReturn(true);
        when(appointmentRepository.findByStaffMemberIdAndBusinessId(3L, businessId)).thenReturn(List.of());
        doThrow(new RuntimeException("Error saving")).when(appointmentRepository).save(appointment);
        assertThrows(RuntimeException.class, () -> appointmentService.saveAppointment(request));
    }

    @Test
    void saveAppointment_whenCustomerNotExist_shouldthrowsException() {
        Long businessId = 1L;
        AppointmentRequestDto request = getRequestDto();
        request.setCustomerId(99L);

        Appointment appointment = new Appointment();
        appointment.setCustomerId(99L);
        appointment.setServiceOfferingId(2L);
        appointment.setStaffMemberId(3L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);
        when(customerRepository.existsByIdAndBusinessId(99L, businessId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.saveAppointment(request)
        );

        assertEquals("Customer not found.", exception.getMessage());

    }

    @Test
    void saveAppointment_whenServiceNotExist_shouldthrowsException() {
        Long businessId = 1L;
        AppointmentRequestDto request = getRequestDto();
        request.setServiceOfferingId(99L);

        Appointment appointment = new Appointment();
        appointment.setCustomerId(1L);
        appointment.setServiceOfferingId(99L);
        appointment.setStaffMemberId(3L);

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
        when(servOfferingRepository.existsByIdAndBusinessId(99L, businessId)).thenReturn(false);
        when(appointmentMapper.toEntity(request)).thenReturn(appointment);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.saveAppointment(request)
        );

        assertEquals("Service offering not found.", exception.getMessage());
        verify(appointmentRepository, never()).save(any());
        verify(staffMemberRepository, never()).existsByIdAndBusinessId(anyLong(), anyLong());
    }

     @Test
    void saveAppointment_whenStaffMemberNotExist_shouldthrowsException() {
        Long businessId = 1L;
        AppointmentRequestDto request = getRequestDto();
        request.setStaffMemberId(99L);

         Appointment appointment = new Appointment();
         appointment.setCustomerId(1L);
         appointment.setServiceOfferingId(2L);
         appointment.setStaffMemberId(99L);

         when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
         when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(servOfferingRepository.existsByIdAndBusinessId(2L, businessId)).thenReturn(true);
         when(staffMemberRepository.existsByIdAndBusinessId(99L, businessId)).thenReturn(false);
         when(appointmentMapper.toEntity(request)).thenReturn(appointment);

         ResourceNotFoundException exception = assertThrows(
                 ResourceNotFoundException.class,
                 () -> appointmentService.saveAppointment(request)
         );

         assertEquals("Staff member not found.", exception.getMessage());
         verify(appointmentRepository, never()).save(any());
     }

     @Test
     void saveAppointment_whenAppointmentsOverlap_shouldThrowsException() {
         Long businessId = 1L;
         AppointmentRequestDto request = getRequestDto();
         request.setCustomerId(1L);
         request.setServiceOfferingId(1L);
         request.setStaffMemberId(1L);
         request.setStartsAt(LocalDateTime.of(2026, 5, 15, 10, 15));

         Appointment existingAppointment = Appointment.builder()
                 .id(1L)
                 .staffMemberId(1L)
                 .startsAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                 .durationMinutes(30)
                 .build();

         Appointment newAppointment = Appointment.builder()
                 .staffMemberId(1L)
                 .customerId(1L)
                 .serviceOfferingId(1L)
                 .startsAt(LocalDateTime.of(2026, 5, 15, 10, 15))
                 .durationMinutes(30)
                 .build();

         when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
         when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(servOfferingRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(staffMemberRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(appointmentRepository.findByStaffMemberIdAndBusinessId(1L, businessId)).thenReturn(List.of(existingAppointment));
         when(appointmentMapper.toEntity(request)).thenReturn(newAppointment);

         // When + Then
         assertThrows(AppointmentOverlapException.class,
                 () -> appointmentService.saveAppointment(request));

         verify(appointmentRepository, never()).save(any());
     }

     @Test
     void saveAppointment_whenAppointmentsDoNotOverlap_shouldSave() {

         Long businessId = 1L;
         AppointmentRequestDto request = getRequestDto();

         Appointment existingAppointment = Appointment.builder()
                 .id(1L)
                 .staffMemberId(1L)
                 .startsAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                 .durationMinutes(30)
                 .build();

         Appointment newAppointment = Appointment.builder()
                 .staffMemberId(1L)
                 .customerId(1L)
                 .serviceOfferingId(1L)
                 .startsAt(LocalDateTime.of(2026, 5, 15, 10, 30))
                 .durationMinutes(30)
                 .build();

         request.setCustomerId(1L);
         request.setServiceOfferingId(1L);
         request.setStaffMemberId(1L);
         request.setStartsAt(LocalDateTime.of(2026, 5, 15, 10, 30));

         when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
         when(appointmentMapper.toEntity(request)).thenReturn(newAppointment);
         when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(servOfferingRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(staffMemberRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
         when(appointmentRepository.findByStaffMemberIdAndBusinessId(1L, businessId)).thenReturn(List.of(existingAppointment));
         when(appointmentRepository.save(newAppointment)).thenReturn(newAppointment);
         when(appointmentMapper.toResponseDto(newAppointment)).thenReturn(AppointmentResponseDto.builder().id(1L).build());

         appointmentService.saveAppointment(request);

         verify(appointmentRepository, times(1)).save(newAppointment);
     }

    @Test
    void findAllAppointments() {
        Long businessId = 1L;

        Appointment appointment1 = new Appointment();
        Appointment appointment2 = new Appointment();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findAllByBusinessId(businessId))
                .thenReturn(Arrays.asList(appointment1, appointment2));

        List<Appointment> appointments = appointmentService.findAllAppointments();

        assertEquals(2, appointments.size());
        assertTrue(appointments.contains(appointment1));
        assertTrue(appointments.contains(appointment2));

        verify(appointmentRepository).findAllByBusinessId(businessId);
    }



    @Test
    void findAppointment() {
        Long id = 1L;
        Appointment appointment = new Appointment();
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId())
                .thenReturn(businessId);

        when(appointmentRepository.findByIdAndBusinessId(id, businessId))
                .thenReturn(Optional.of(appointment));


        Appointment result = appointmentService.findAppointment(id);

        assertNotNull(result);
        assertEquals(appointment, result);
        verify(appointmentRepository).findByIdAndBusinessId(id, businessId);
    }

    @Test
    void findAppointment_whenNotExist_throwsException() {
        Long businessId = 1L;
        Long id = 99L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.findAppointment(id)
        );

        assertEquals("Appointment not found with ID: 99", exception.getMessage());
        verify(appointmentRepository).findByIdAndBusinessId(id, businessId);
    }

    @Test
    void updateAppointment_shouldUpdateAndSave() {
        Long id = 1L;
        Long businessId = 1L;

        LocalDateTime originalCreatedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime originalUpdatedAt = LocalDateTime.now().minusDays(1);

        Appointment current = Appointment.builder()
                .id(id)
                .businessId(businessId)
                .customerId(1L)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.of(2026, 2, 15, 9, 0))
                .durationMinutes(30)
                .priceCents(10000)
                .createdAt(originalCreatedAt)
                .updatedAt(originalUpdatedAt)
                .build();

        AppointmentUpdateRequestDto request = getUpdateRequestDto();
        request.setCustomerId(10L);
        request.setStaffMemberId(30L);
        request.setStartsAt(LocalDateTime.of(2026, 2, 15, 10, 0));

        AppointmentResponseDto response = AppointmentResponseDto.builder()
                .id(id)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(current));
        when(customerRepository.existsByIdAndBusinessId(10L, businessId)).thenReturn(true);
        when(staffMemberRepository.existsByIdAndBusinessId(30L, businessId)).thenReturn(true);
        when(appointmentRepository.findByStaffMemberIdAndBusinessId(30L, businessId)).thenReturn(List.of());
        when(appointmentRepository.save(current)).thenReturn(current);
        when(appointmentMapper.toResponseDto(current)).thenReturn(response);

        appointmentService.updateAppointment(id, request);

        verify(appointmentRepository).save(current);

        assertEquals(10L, current.getCustomerId());
        assertEquals(30L, current.getStaffMemberId());
        assertEquals(originalCreatedAt, current.getCreatedAt());
        assertTrue(current.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void updateAppointment_whenAppointmentOverlap_throwsException() {
        Long id = 1L;
        Long businessId = 1L;

        Appointment currentAppointment = Appointment.builder()
                .id(id)
                .businessId(businessId)
                .customerId(1L)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.of(2026, 2, 15, 9, 0))
                .durationMinutes(30)
                .build();

        Appointment overlappingAppointment = Appointment.builder()
                .id(2L)
                .staffMemberId(3L)
                .startsAt(LocalDateTime.of(2026, 2, 15, 10, 0))
                .durationMinutes(60)
                .build();

        AppointmentUpdateRequestDto request = getUpdateRequestDto();
        request.setStartsAt(LocalDateTime.of(2026, 2, 15, 10, 30));
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(currentAppointment));
        when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
        when(staffMemberRepository.existsByIdAndBusinessId(3L, businessId)).thenReturn(true);
        when(appointmentRepository.findByStaffMemberIdAndBusinessId(3L, businessId)).thenReturn(List.of(currentAppointment, overlappingAppointment));

        assertThrows(AppointmentOverlapException.class, () -> appointmentService.updateAppointment(id, request));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void updateAppointment_shouldNotDetectOverlapWithSameAppointment() {
        // Given
        Long id = 1L;
        Long businessId = 1L;

        Appointment existingAppointment = Appointment.builder()
                .id(id)
                .businessId(businessId)
                .staffMemberId(1L)
                .customerId(1L)
                .serviceOfferingId(2L)
                .startsAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                .durationMinutes(30)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        AppointmentUpdateRequestDto request = getUpdateRequestDto();
        request.setCustomerId(1L);
        request.setServiceOfferingId(2L);
        request.setStaffMemberId(1L);

        // When + then
        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(existingAppointment));
        when(customerRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
        when(staffMemberRepository.existsByIdAndBusinessId(1L, businessId)).thenReturn(true);
        when(appointmentRepository.findByStaffMemberIdAndBusinessId(1L, businessId)).thenReturn(List.of(existingAppointment));
        when(appointmentRepository.save(existingAppointment)).thenReturn(existingAppointment);
        when(appointmentMapper.toResponseDto(existingAppointment)).thenReturn(AppointmentResponseDto.builder().id(id).build());

        appointmentService.updateAppointment(id, request);
        verify(appointmentRepository).save(existingAppointment);
    }

    @Test
    void updateAppointment_whenServiceChanges_shouldRecalculateSnapshots() {
        Long id = 1L;
        Long businessId = 1L;

        LocalDateTime startsAt = LocalDateTime.of(2026, 5, 15, 10, 0);
        LocalDateTime originalCreatedAt = LocalDateTime.now().minusDays(1);
        LocalDateTime originalUpdatedAt = LocalDateTime.now().minusDays(1);

        Appointment existingAppointment = Appointment.builder()
                .id(id)
                .businessId(businessId)
                .customerId(1L)
                .serviceOfferingId(2L)
                .staffMemberId(3L)
                .startsAt(startsAt)
                .endsAt(startsAt.plusMinutes(30))
                .durationMinutes(30)
                .priceCents(10000)
                .createdAt(originalCreatedAt)
                .updatedAt(originalUpdatedAt)
                .build();

        AppointmentUpdateRequestDto request = AppointmentUpdateRequestDto.builder()
                .serviceOfferingId(20L)
                .build();

        ServiceOffering newService = ServiceOffering.builder()
                .id(20L)
                .businessId(businessId)
                .durationMinutes(60)
                .priceCents(25000)
                .build();

        AppointmentResponseDto responseDto = AppointmentResponseDto.builder()
                .id(id)
                .serviceOfferingId(20L)
                .durationMinutes(60)
                .priceCents(25000)
                .startsAt(startsAt)
                .endsAt(startsAt.plusMinutes(60))
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(existingAppointment));
        when(servOfferingRepository.findByIdAndBusinessId(20L, businessId)).thenReturn(Optional.of(newService));
        when(appointmentRepository.findByStaffMemberIdAndBusinessId(3L, businessId)).thenReturn(List.of());
        when(appointmentRepository.save(existingAppointment)).thenReturn(existingAppointment);
        when(appointmentMapper.toResponseDto(existingAppointment)).thenReturn(responseDto);

        AppointmentResponseDto result = appointmentService.updateAppointment(id, request);
        assertNotNull(result);
        assertEquals(20L, existingAppointment.getServiceOfferingId());
        assertEquals(60, existingAppointment.getDurationMinutes());
        assertEquals(25000, existingAppointment.getPriceCents());
        assertEquals(startsAt.plusMinutes(60), existingAppointment.getEndsAt());
        assertEquals(originalCreatedAt, existingAppointment.getCreatedAt());
        assertTrue(existingAppointment.getUpdatedAt().isAfter(originalUpdatedAt));

        verify(appointmentRepository).save(existingAppointment);
    }

    @Test
    void confirmAppointment_whenPending_shouldConfirmAndSave() {
        Long appointmentId = 1L;
        Long businessId = 1L;

        LocalDateTime originalUpdatedAt = LocalDateTime.now().minusDays(1);

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .businessId(businessId)
                .status(AppointmentStatus.PENDING)
                .updatedAt(originalUpdatedAt)
                .build();

        AppointmentResponseDto responseDto = AppointmentResponseDto.builder()
                .id(appointmentId)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(appointmentId, businessId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponseDto(appointment)).thenReturn(responseDto);

        AppointmentResponseDto result = appointmentService.confirmAppointment(appointmentId);

        assertNotNull(result);
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        assertTrue(appointment.getUpdatedAt().isAfter(originalUpdatedAt));

        verify(appointmentRepository).findByIdAndBusinessId(appointmentId, businessId);
        verify(appointmentRepository).save(appointment);
        verify(appointmentMapper).toResponseDto(appointment);
    }

    @Test
    void confirmAppointment_whenNotPending_shouldThrowException() {
        Long appointmentId = 1L;
        Long businessId = 1L;

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .businessId(businessId)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(appointmentId, businessId)).thenReturn(Optional.of(appointment));

        InvalidStateTransitionException exception = assertThrows(InvalidStateTransitionException.class, ()
                -> appointmentService.confirmAppointment(appointmentId));

        assertEquals("Cannot transition appointment from CONFIRMED to CONFIRMED", exception.getMessage());

        verify(appointmentRepository, never()).save(any());
        verify(appointmentMapper, never()).toResponseDto(any());
    }

    @Test
    void cancelAppointment_whenPending_shouldCancelAndSaveReason(){
        Long appointmentId = 1L;
        Long businessId = 1L;

        LocalDateTime originalUpdatedAt = LocalDateTime.now().minusDays(1);

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .businessId(businessId)
                .status(AppointmentStatus.PENDING)
                .updatedAt(originalUpdatedAt)
                .build();

        AppointmentCancelRequestDto request = AppointmentCancelRequestDto.builder()
                .cancellationReason("Customer requested cancellation")
                .build();

        AppointmentResponseDto responseDto = AppointmentResponseDto.builder()
                .id(appointmentId)
                .status(AppointmentStatus.CANCELLED)
                .cancellationReason("Customer requested cancellation")
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(appointmentId, businessId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponseDto(appointment)).thenReturn(responseDto);

        AppointmentResponseDto result = appointmentService.cancelAppointment(appointmentId, request);

        assertNotNull(result);
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals("Customer requested cancellation", appointment.getCancellationReason());
        assertTrue(appointment.getUpdatedAt().isAfter(originalUpdatedAt));

        verify(appointmentRepository).findByIdAndBusinessId(appointmentId, businessId);
        verify(appointmentRepository).save(appointment);
        verify(appointmentMapper).toResponseDto(appointment);

    }

    @Test
    void cancelAppointment_whenConfirmed_shouldCancel(){
        Long appointmentId = 1L;
        Long businessId = 1L;

        LocalDateTime originalUpdatedAt = LocalDateTime.now().minusDays(1);

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .businessId(businessId)
                .status(AppointmentStatus.CONFIRMED)
                .updatedAt(originalUpdatedAt)
                .build();

        AppointmentCancelRequestDto request = AppointmentCancelRequestDto.builder()
                .cancellationReason("Customer requested cancellation")
                .build();

        AppointmentResponseDto responseDto = AppointmentResponseDto.builder()
                .id(appointmentId)
                .status(AppointmentStatus.CANCELLED)
                .cancellationReason("Customer requested cancellation")
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(appointmentId, businessId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponseDto(appointment)).thenReturn(responseDto);

        AppointmentResponseDto result = appointmentService.cancelAppointment(appointmentId, request);

        assertNotNull(result);
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals("Customer requested cancellation", appointment.getCancellationReason());
        assertTrue(appointment.getUpdatedAt().isAfter(originalUpdatedAt));

        verify(appointmentRepository).findByIdAndBusinessId(appointmentId, businessId);
        verify(appointmentRepository).save(appointment);
        verify(appointmentMapper).toResponseDto(appointment);
    }

    @Test
    void cancelAppointment_whenAlreadyCancelled_shouldThrowException() {
        Long appointmentId = 1L;
        Long businessId = 1L;

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .businessId(businessId)
                .status(AppointmentStatus.CANCELLED)
                .build();

        AppointmentCancelRequestDto request = AppointmentCancelRequestDto.builder()
                        .cancellationReason("Another reason")
                        .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(appointmentId, businessId)).thenReturn(Optional.of(appointment));

        InvalidStateTransitionException exception = assertThrows(InvalidStateTransitionException.class, ()
                -> appointmentService.cancelAppointment(appointmentId, request));

        assertEquals("Cannot transition appointment from CANCELLED to CANCELLED", exception.getMessage());
        verify(appointmentRepository, never()).save(any());
        verify(appointmentMapper, never()).toResponseDto(any());
    }

    @Test
    void deleteAppointment_whenExists_shouldDelete() {
        Long id = 1L;
        Long businessId = 1L;

        Appointment appointment = new Appointment();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(appointment));

        appointmentService.deleteAppointment(id);

        verify(appointmentRepository).delete(appointment);
    }

    @Test
    void deleteAppointment_whenDoesNotExist_shouldNotDelete() {
        Long id = 99L;
        Long businessId = 1L;

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);

        when(appointmentRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.deleteAppointment(id)
        );

        assertEquals("Appointment not found with ID: 99", exception.getMessage());

        verify(appointmentRepository, never()).delete(any(Appointment.class));    }
}



