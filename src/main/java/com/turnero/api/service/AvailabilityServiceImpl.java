package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.AvailabilitySlotResponseDto;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.AvailabilityExceptionsType;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvailabilityServiceImpl implements AvailabilityService{

    private final CurrentBusinessContext currentBusinessContext;
    private final BusinessHoursRepository businessHoursRepository;
    private final StaffWorkingHoursRepository staffWorkingHoursRepository;
    private final AvailabilityExceptionRepository availabilityExceptionRepository;
    private final BookingSettingsRepository bookingSettingsRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;


    private List<Appointment> excludeCurrentAppointment(List<Appointment> appointments, Long excludeAppointmentId) {
        if (excludeAppointmentId == null) {
            return appointments;
        }

        return appointments.stream().filter(appointment -> !appointment.getId().equals(excludeAppointmentId)).toList();
    }

    private boolean hasOverlap(LocalDateTime slotStart, LocalDateTime slotEnd, List<Appointment> blockingAppointments) {
        return blockingAppointments.stream()
                .anyMatch(appointment -> slotStart.isBefore(appointment.getEndsAt())
                                && slotEnd.isAfter(appointment.getStartsAt())
                );
    }

    private boolean hasBlockedOverlap(LocalDateTime slotStart, LocalDateTime slotEnd, LocalDate currentDate,
                                      List<AvailabilityException> availabilityExceptions) {
        return availabilityExceptions.stream()
                .filter(exception -> exception.getDate().equals(currentDate))
                .filter(exception -> exception.getType() == AvailabilityExceptionsType.BLOCKED)
                .anyMatch(exception -> {
                    LocalDateTime blockedStart = LocalDateTime.of(currentDate, exception.getStartsAt());
                    LocalDateTime blockedEnd = LocalDateTime.of(currentDate, exception.getEndsAt());
                    return slotStart.isBefore(blockedEnd) && slotEnd.isAfter(blockedStart);
                });
    }

    private WorkingWindow resolveWorkingWindow(LocalDate currentDate, List<BusinessHours> businessHours, List<StaffWorkingHours> staffWorkingHours) {
        DayOfWeek currentDayOfWeek = DayOfWeek.valueOf(currentDate.getDayOfWeek().name());

        var businessHoursForDay = businessHours.stream()
                .filter(hours -> hours.getDayOfWeek() == currentDayOfWeek).findFirst();

        var staffHoursForDay = staffWorkingHours.stream()
                .filter(hours -> hours.getDayOfWeek() == currentDayOfWeek).findFirst();

        if (businessHoursForDay.isEmpty() || staffHoursForDay.isEmpty()) {
            return null;
        }

        var businessHoursValue = businessHoursForDay.get();
        var staffHoursValue = staffHoursForDay.get();

        if (businessHoursValue.isClosed() || !staffHoursValue.isAvailable()) {
            return null;
        }

        var startTime = businessHoursValue.getOpensAt()
                .isAfter(staffHoursValue.getStartsAt())
                ? businessHoursValue.getOpensAt()
                : staffHoursValue.getStartsAt();

        var endTime = businessHoursValue.getClosesAt()
                .isBefore(staffHoursValue.getEndsAt())
                ? businessHoursValue.getClosesAt()
                : staffHoursValue.getEndsAt();

        if (!startTime.isBefore(endTime)) {
            return null;
        }

        return new WorkingWindow(LocalDateTime.of(currentDate, startTime), LocalDateTime.of(currentDate, endTime)
        );
    }

    private List<AvailabilitySlotResponseDto> generateSlotsForDate(LocalDate currentDate, int serviceDurationMinutes,
            int slotIntervalMinutes, List<BusinessHours> businessHours, List<StaffWorkingHours> staffWorkingHours,
            List<AvailabilityException> availabilityExceptions, List<Appointment> blockingAppointments) {

        WorkingWindow workingWindow = resolveWorkingWindow(currentDate, businessHours, staffWorkingHours);

        workingWindow = applyAvailabilityExceptions(workingWindow, currentDate, availabilityExceptions);
        if (workingWindow == null) {
            return List.of();
        }

        var slots = new ArrayList<AvailabilitySlotResponseDto>();

        var slotStart = workingWindow.startsAt();
        var workingEnd = workingWindow.endsAt();

        while (!slotStart.plusMinutes(serviceDurationMinutes).isAfter(workingEnd)) {

            var slotEnd = slotStart.plusMinutes(serviceDurationMinutes);

            if (!hasBlockedOverlap(slotStart, slotEnd, currentDate, availabilityExceptions) &&
                    !hasOverlap(slotStart, slotEnd, blockingAppointments)) {

                slots.add(AvailabilitySlotResponseDto.builder()
                                .startsAt(slotStart)
                                .endsAt(slotEnd)
                                .available(true)
                                .build()
                );
            }

            slotStart = slotStart.plusMinutes(slotIntervalMinutes);
        }

        return slots;
    }

    @Override
    public List<AvailabilitySlotResponseDto> getAvailableSlots(LocalDate from, LocalDate to, Long serviceOfferingId,
            Long staffMemberId, Long excludeAppointmentId) {

        Long businessId = currentBusinessContext.getCurrentBusinessId();

        validateDateRange(from, to);

        ServiceOffering serviceOffering = servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found."));

        if (!staffMemberRepository.existsByIdAndBusinessId(staffMemberId, businessId)) {
            throw new ResourceNotFoundException("Staff member not found.");
        }

        var bookingSettings = bookingSettingsRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking settings not found."));

        var businessHours = businessHoursRepository.findAllByBusinessId(businessId);

        var staffWorkingHours = staffWorkingHoursRepository.findAllByStaffMemberId(staffMemberId);

        var availabilityExceptions = availabilityExceptionRepository.findRelevantExceptions(businessId, staffMemberId,
                from, to);

        var rangeStart = from.atStartOfDay();
        var rangeEnd = to.plusDays(1).atStartOfDay();

        var blockingAppointments =
                appointmentRepository.findBlockingAppointments(
                        businessId,
                        staffMemberId,
                        List.of(
                                AppointmentStatus.PENDING,
                                AppointmentStatus.CONFIRMED
                        ),
                        rangeStart,
                        rangeEnd
                );

        blockingAppointments = excludeCurrentAppointment(
                blockingAppointments,
                excludeAppointmentId
        );

        int serviceDurationMinutes = serviceOffering.getDurationMinutes();

        if (serviceDurationMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Service duration must be greater than zero"
            );
        }

        int slotIntervalMinutes = bookingSettings.getSlotIntervalMinutes();

        if (slotIntervalMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Slot interval must be greater than zero"
            );
        }

        var availableSlots = new ArrayList<AvailabilitySlotResponseDto>();

        for (LocalDate currentDate = from; !currentDate.isAfter(to); currentDate = currentDate.plusDays(1)) {
            availableSlots.addAll(generateSlotsForDate(currentDate, serviceDurationMinutes, slotIntervalMinutes,
                            businessHours, staffWorkingHours, availabilityExceptions, blockingAppointments));
        }

        return availableSlots;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required.");
        }

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to.");
        }

        long rangeDays = ChronoUnit.DAYS.between(from, to) + 1;

        if (rangeDays > 31) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed 31 days.");
        }
    }

    private WorkingWindow applyAvailabilityExceptions(WorkingWindow workingWindow, LocalDate currentDate,
            List<AvailabilityException> availabilityExceptions) {

        var exceptionsForDate = availabilityExceptions.stream()
                .filter(exception -> exception.getDate().equals(currentDate))
                .toList();

        for (var exception : exceptionsForDate) {
            switch (exception.getType()) {
                case CLOSED:
                    return null;

                case SPECIAL_HOURS:
                    return new WorkingWindow(
                            LocalDateTime.of(currentDate, exception.getStartsAt()),
                            LocalDateTime.of(currentDate, exception.getEndsAt())
                    );

                case BLOCKED:
                    break;
            }
        }

        return workingWindow;
    }

    private record WorkingWindow(LocalDateTime startsAt, LocalDateTime endsAt) {
    }
}
