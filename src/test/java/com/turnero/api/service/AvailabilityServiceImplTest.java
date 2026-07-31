package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.AvailabilitySlotResponseDto;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AvailabilityExceptionsType;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class AvailabilityServiceImplTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BusinessHoursRepository businessHoursRepository;

    @Mock
    private StaffWorkingHoursRepository staffWorkingHoursRepository;

    @Mock
    private AvailabilityExceptionRepository availabilityExceptionRepository;

    @Mock
    private ServOfferingRepository servOfferingRepository;

    @Mock
    private StaffMemberRepository staffMemberRepository;

    @Mock
    private CurrentBusinessContext currentBusinessContext;

    @Mock
    private BookingSettingsRepository bookingSettingsRepository;

    private AvailabilityServiceImpl availabilityService;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityServiceImpl(currentBusinessContext, businessHoursRepository, staffWorkingHoursRepository,
                availabilityExceptionRepository, bookingSettingsRepository, appointmentRepository, servOfferingRepository, staffMemberRepository);
    }

    @Test
    void getAvailableSlots_whenScheduleIsAvailable_shouldReturnSlots(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of());
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of());
        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                        staffMemberId, null);

        assertEquals(LocalDateTime.of(2024, 6, 10, 9, 0), result.get(0).getStartsAt());
        assertEquals(LocalDateTime.of(2024, 6, 10, 9, 30), result.get(0).getEndsAt());
        assertTrue(result.get(0).isAvailable());
        assertEquals(LocalDateTime.of(2024, 6, 10, 9, 30), result.get(1).getStartsAt());
        assertEquals(LocalDateTime.of(2024, 6, 10, 10, 0), result.get(1).getEndsAt());
        assertTrue(result.get(1).isAvailable());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );

    }

    @Test
    void getAvailableSlots_whenBusinessIsClosed_shouldReturnEmptyList(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(true)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of());
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of());
        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                staffMemberId, null);


        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );
    }

    @Test
    void getAvailableSlots_whenStaffMemberIsUnavailable_shouldReturnEmptyList(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(false)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of());
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of());

        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                staffMemberId, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );
    }

    @Test
    void getAvailableSlots_whenPendingAppointmentOverlaps_shouldExcludeSlot(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();

        Appointment appointment = Appointment.builder()
                .businessId(businessId)
                .staffMemberId(staffMemberId)
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.PENDING)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of());
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of(appointment));

        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                        staffMemberId, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 30)), result.get(0).getStartsAt());
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), result.get(0).getEndsAt());
        assertTrue(result.get(0).isAvailable());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );
    }

    @Test
    void getAvailableSlots_whenConfirmedAppointmentOverlaps_shouldExcludeSlot(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();

        Appointment appointment = Appointment.builder()
                .businessId(businessId)
                .staffMemberId(staffMemberId)
                .startsAt(LocalDateTime.of(date, LocalTime.of(9, 0)))
                .endsAt(LocalDateTime.of(date, LocalTime.of(9, 30)))
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of());
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of(appointment));

        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                staffMemberId, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 30)), result.get(0).getStartsAt());
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), result.get(0).getEndsAt());
        assertTrue(result.get(0).isAvailable());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );
    }

    @Test
    void getAvailableSlots_whenCancelledAppointmentOverlaps_shouldNotExcludeSlot(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of());
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of());

        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                staffMemberId, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 0)), result.get(0).getStartsAt());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 30)), result.get(0).getEndsAt());
        assertTrue(result.get(0).isAvailable());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 30)), result.get(1).getStartsAt());
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), result.get(1).getEndsAt());
        assertTrue(result.get(1).isAvailable());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );

    }

    @Test
    void getAvailableSlots_whenBlockedExceptionOverlaps_shouldExcludeBlockedSlots(){
        Long businessId = 1L;
        Long serviceOfferingId = 1L;
        Long staffMemberId = 1L;

        LocalDate date = LocalDate.of(2024, 6, 10);
        LocalDateTime rangeStart = date.atStartOfDay();
        LocalDateTime rangeEnd = date.plusDays(1).atStartOfDay();

        ServiceOffering serviceOffering = ServiceOffering.builder()
                .id(serviceOfferingId)
                .businessId(businessId)
                .durationMinutes(30)
                .build();

        BookingSettings bookingSettings = BookingSettings.builder()
                .businessId(businessId)
                .slotIntervalMinutes(30)
                .build();

        BusinessHours businessHours = BusinessHours.builder()
                .businessId(businessId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .opensAt(LocalTime.of(9, 0))
                .closesAt(LocalTime.of(10, 0))
                .isClosed(false)
                .build();

        StaffWorkingHours staffWorkingHours = StaffWorkingHours.builder()
                .staffMemberId(staffMemberId)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(10, 0))
                .isAvailable(true)
                .build();

        AvailabilityException blockedException = AvailabilityException.builder()
                .date(date)
                .startsAt(LocalTime.of(9, 0))
                .endsAt(LocalTime.of(9, 30))
                .type(AvailabilityExceptionsType.BLOCKED)
                .build();

        when(currentBusinessContext.getCurrentBusinessId()).thenReturn(businessId);
        when(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)).thenReturn(Optional.of(serviceOffering));
        when(staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)).thenReturn(true);
        when(bookingSettingsRepository.findByBusinessId(businessId)).thenReturn(Optional.of(bookingSettings));
        when(businessHoursRepository.findAllByBusinessId(businessId)).thenReturn(List.of(businessHours));
        when(staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId)).thenReturn(List.of(staffWorkingHours));
        when(appointmentRepository.findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        )).thenReturn(List.of());
        when(availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId, date, date)).thenReturn(List.of(blockedException));

        List<AvailabilitySlotResponseDto> result = availabilityService.getAvailableSlots(date, date, serviceOfferingId,
                staffMemberId, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 30)), result.get(0).getStartsAt());
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), result.get(0).getEndsAt());
        assertTrue(result.get(0).isAvailable());

        verify(currentBusinessContext).getCurrentBusinessId();
        verify(servOfferingRepository).findByIdAndBusinessId(serviceOfferingId, businessId);
        verify(staffMemberRepository).existsByIdAndBusinessId(staffMemberId, businessId);
        verify(bookingSettingsRepository).findByBusinessId(businessId);
        verify(businessHoursRepository).findAllByBusinessId(businessId);
        verify(staffWorkingHoursRepository).findAllByStaffMemberId(staffMemberId);
        verify(availabilityExceptionRepository).findRelevantExceptions(businessId, staffMemberId, date, date);
        verify(appointmentRepository).findBlockingAppointments(
                businessId,
                staffMemberId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                ),
                rangeStart,
                rangeEnd
        );

    }
}
