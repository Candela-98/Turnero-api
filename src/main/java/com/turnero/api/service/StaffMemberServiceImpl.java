package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.StaffMemberUpdateRequestDto;
import com.turnero.api.dto.StaffWorkingHoursRequestDto;
import com.turnero.api.dto.StaffWorkingHoursResponseDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BusinessHours;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.StaffWorkingHours;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.DayOfWeek;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.BusinessHoursRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffWorkingHoursRepository;
import com.turnero.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class StaffMemberServiceImpl implements StaffMemberService {

    private final CurrentBusinessContext currentBusinessContext;
    private final BusinessHoursRepository businessHoursRepository;
    private final StaffWorkingHoursRepository staffWorkingHoursRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    public StaffMember saveStaffMember(StaffMember staffMember) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        if (staffMember.getUserId() != null &&
                !userRepository.existsByIdAndBusinessId(staffMember.getUserId(), businessId)) {
            throw new ResourceNotFoundException("User not found for current business");
        }

        List<BusinessHours> businessHours = businessHoursRepository.findAllByBusinessId(businessId);

        if (businessHours.isEmpty()) {
            throw new ResourceNotFoundException("Business hours not found for current business");
        }

        LocalDateTime now = LocalDateTime.now();

        staffMember.setBusinessId(businessId);
        staffMember.setStatus(StaffMemberStatus.ACTIVE);
        staffMember.setCreatedAt(now);
        staffMember.setUpdatedAt(now);

        StaffMember savedStaffMember = staffMemberRepository.save(staffMember);

        List<StaffWorkingHours> staffWorkingHours = businessHours.stream()
                .map(hour -> StaffWorkingHours.builder()
                        .staffMemberId(savedStaffMember.getId())
                        .dayOfWeek(hour.getDayOfWeek())
                        .startsAt(hour.getOpensAt())
                        .endsAt(hour.getClosesAt())
                        .isAvailable(!hour.isClosed())
                        .build())
                .toList();

        staffWorkingHoursRepository.saveAll(staffWorkingHours);

        log.info(
                "Staff member created with id={} for businessId={}",
                savedStaffMember.getId(),
                businessId
        );

        return savedStaffMember;
    }

    @Override
    public StaffMember findStaffMember(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        return staffMemberRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staffmember not found with ID: " + id));
    }

    @Override
    public StaffMember updateStaffMember(StaffMemberUpdateRequestDto staffMember, Long id) {
        StaffMember staffMemberExist = findStaffMember(id);

        if (staffMember.getName() != null) {
            staffMemberExist.setName(staffMember.getName());
        }

        if (staffMember.getRoleLabel() != null) {
            staffMemberExist.setRoleLabel(staffMember.getRoleLabel());
        }

        if (staffMember.getSpecialty() != null) {
            staffMemberExist.setSpecialty(staffMember.getSpecialty());
        }

        if (staffMember.getAvatarUrl() != null) {
            staffMemberExist.setAvatarUrl(staffMember.getAvatarUrl());
        }

        if (staffMember.getStatus() != null) {
            staffMemberExist.setStatus(staffMember.getStatus());
        }

        StaffMember updatedStaffMember = staffMemberRepository.save(staffMemberExist);
        log.info("Staffmember with id={} successfully updated.", id);

        return updatedStaffMember;
    }

    @Override
    public List<StaffMember> findAllStaffMember() {

        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return staffMemberRepository.findAllByBusinessId(businessId);
    }

    @Override
    public List<StaffWorkingHoursResponseDto> getWorkingHours(Long staffMemberId) {

        findStaffMember(staffMemberId);

        return staffWorkingHoursRepository
                .findAllByStaffMemberIdOrderByDayOfWeekAsc(staffMemberId)
                .stream()
                .map(hour -> StaffWorkingHoursResponseDto.builder()
                        .dayOfWeek(hour.getDayOfWeek())
                        .startsAt(hour.getStartsAt())
                        .endsAt(hour.getEndsAt())
                        .isAvailable(hour.isAvailable())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public List<StaffWorkingHoursResponseDto> replaceWorkingHours(Long staffMemberId, List<StaffWorkingHoursRequestDto> workingHours) {

        findStaffMember(staffMemberId);

        validateWorkingHours(workingHours);

        staffWorkingHoursRepository.deleteAllByStaffMemberId(staffMemberId);

        List<StaffWorkingHours> newWorkingHours = workingHours.stream()
                .map(workingHour -> StaffWorkingHours.builder()
                        .staffMemberId(staffMemberId)
                        .dayOfWeek(workingHour.getDayOfWeek())
                        .startsAt(workingHour.getStartsAt())
                        .endsAt(workingHour.getEndsAt())
                        .isAvailable(Boolean.TRUE.equals(workingHour.getIsAvailable()))
                        .build())
                .toList();

        List<StaffWorkingHours> savedWorkingHours = staffWorkingHoursRepository.saveAll(newWorkingHours);

        return savedWorkingHours.stream()
                .map(hour -> StaffWorkingHoursResponseDto.builder()
                        .dayOfWeek(hour.getDayOfWeek())
                        .startsAt(hour.getStartsAt())
                        .endsAt(hour.getEndsAt())
                        .isAvailable(hour.isAvailable())
                        .build())
                .toList();
    }

    private void validateWorkingHours(List<StaffWorkingHoursRequestDto> workingHours) {
        if (workingHours.size() != 7) {
            throw new IllegalArgumentException("Working hours must contain exactly 7 days");
        }

        Set<DayOfWeek> days = workingHours.stream()
                .map(StaffWorkingHoursRequestDto::getDayOfWeek)
                .collect(Collectors.toSet());

        if (days.size() != 7) {
            throw new IllegalArgumentException("Working hours must contain all 7 unique days");
        }

        for (StaffWorkingHoursRequestDto workingHour : workingHours) {

            if (Boolean.TRUE.equals(workingHour.getIsAvailable()) && workingHour.getStartsAt() == null) {

                throw new IllegalArgumentException("Start time is required when the day is available");
            }

            if( Boolean.TRUE.equals(workingHour.getIsAvailable()) && workingHour.getEndsAt() == null) {
                throw new IllegalArgumentException("End time is required when the day is available");
            }

            if (Boolean.TRUE.equals(workingHour.getIsAvailable()) &&
                    workingHour.getStartsAt() != null &&
                    workingHour.getEndsAt() != null &&
                    !workingHour.getStartsAt().isBefore(workingHour.getEndsAt())) {
                throw new IllegalArgumentException("Start time must be before end time for available days");
            }
        }
    }

    @Override
    public void deleteStaffMember(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        StaffMember staffMember = staffMemberRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staffmember not found with ID: " + id));

        boolean hasFutureActiveAppointments =
                appointmentRepository.existsByBusinessIdAndStaffMemberIdAndStatusInAndStartsAtAfter(
                        businessId,
                        id,
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                        LocalDateTime.now()
                );

        if (hasFutureActiveAppointments) {
            throw new AppointmentOverlapException(
                    "Staff member cannot be deactivated because it has future active appointments"
            );
        }

        staffMember.setStatus(StaffMemberStatus.INACTIVE);
        staffMember.setUpdatedAt(LocalDateTime.now());

        staffMemberRepository.save(staffMember);
        log.info("Staffmember with id={} successfully deactivated for businessId={}.", id, businessId);
    }
}
