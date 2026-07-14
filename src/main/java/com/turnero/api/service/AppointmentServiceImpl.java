package com.turnero.api.service;

import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.AppointmentRequestDto;
import com.turnero.api.dto.AppointmentResponseDto;
import com.turnero.api.dto.AppointmentUpdateRequestDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.mapper.AppointmentMapper;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.Customer;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServOfferingRepository serviceRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final CurrentBusinessContext currentBusinessContext;
    private final AppointmentMapper appointmentMapper;


    private Long resolveCustomerId(AppointmentRequestDto request, Long businessId) {
        if (request.getCustomerId() != null) {
            return request.getCustomerId();
        }

        Customer customer = Customer.builder()
                .businessId(businessId)
                .name(request.getCustomerName())
                .email(request.getCustomerEmail())
                .build();

        Customer savedCustomer = customerRepository.save(customer);

        return savedCustomer.getId();
    }

    private void validateReferences(Appointment appointment, Long businessId) {
        if (!customerRepository.existsByIdAndBusinessId(appointment.getCustomerId(), businessId)) {
            throw new ResourceNotFoundException("Customer not found.");
        }
        if (!serviceRepository.existsByIdAndBusinessId(appointment.getServiceOfferingId(), businessId)) {
            throw new ResourceNotFoundException("Service offering not found.");
        }
        if (!staffMemberRepository.existsByIdAndBusinessId(appointment.getStaffMemberId(), businessId)) {
            throw new ResourceNotFoundException("Staff member not found.");
        }
    }

    private void validateNoOverlap(Long staffMemberId, LocalDateTime newStart,int durationMinutes,
                                   Long appointmentIdToExclude, Long businessId) {

        LocalDateTime newEnd = newStart.plusMinutes(durationMinutes);
        List<Appointment> staffAppointments = appointmentRepository.findByStaffMemberIdAndBusinessId(
                        staffMemberId,
                        businessId);
        boolean hasOverlap = staffAppointments.stream()
                .filter(existing -> appointmentIdToExclude == null
                        || !existing.getId().equals(appointmentIdToExclude))
                .anyMatch(existing -> {
                    LocalDateTime existingStart = existing.getStartsAt();
                    LocalDateTime existingEnd = existingStart.plusMinutes(existing.getDurationMinutes());
                    return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
                });

        if(hasOverlap) {
            throw new AppointmentOverlapException("Staff member already has an appointment in this time range");
        }
    }

    @Override
    public AppointmentResponseDto saveAppointment(AppointmentRequestDto request) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        Long customerId = resolveCustomerId(request, businessId);

        Appointment appointment = appointmentMapper.toEntity(request);
        appointment.setBusinessId(businessId);
        appointment.setCustomerId(customerId);

        validateReferences(appointment, businessId);

        validateNoOverlap(
                appointment.getStaffMemberId(),
                appointment.getStartsAt(),
                appointment.getDurationMinutes(),
                null,
                businessId
        );

        LocalDateTime now = LocalDateTime.now();
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);

        Appointment savedAppointment = appointmentRepository.save(appointment);

        log.info("Appointment created with id={} businessId={}", savedAppointment.getId(), businessId);

        return appointmentMapper.toResponseDto(savedAppointment);
    }

    @Override
    public List<Appointment> findAllAppointments() {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return appointmentRepository.findAllByBusinessId(businessId);
    }

    @Override
    public Appointment findAppointment(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();
        return appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with ID: " + id));
    }

    @Override
    public AppointmentResponseDto updateAppointment(Long id, AppointmentUpdateRequestDto request) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        Appointment existingAppointment = appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with ID: " + id));

        if (request.getCustomerId() != null) {
            if (!customerRepository.existsByIdAndBusinessId(request.getCustomerId(), businessId)) {
                throw new ResourceNotFoundException("Customer not found.");
            }
            existingAppointment.setCustomerId(request.getCustomerId());
        }

        if (request.getStaffMemberId() != null) {
            if (!staffMemberRepository.existsByIdAndBusinessId(request.getStaffMemberId(), businessId)) {
                throw new ResourceNotFoundException("Staff member not found.");
            }
            existingAppointment.setStaffMemberId(request.getStaffMemberId());
        }

        boolean serviceChanged = request.getServiceOfferingId() != null && !request.getServiceOfferingId()
                .equals(existingAppointment.getServiceOfferingId());

        if (serviceChanged) {
            ServiceOffering serviceOffering = serviceRepository.findByIdAndBusinessId(request.getServiceOfferingId(), businessId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service offering not found."));

            existingAppointment.setServiceOfferingId(serviceOffering.getId());
            existingAppointment.setDurationMinutes(serviceOffering.getDurationMinutes());
            existingAppointment.setPriceCents(serviceOffering.getPriceCents());
        }

        if (request.getStartsAt() != null) {
            existingAppointment.setStartsAt(request.getStartsAt());
        }

        if (serviceChanged || request.getStartsAt() != null) {
            existingAppointment.setEndsAt(existingAppointment.getStartsAt().plusMinutes(
                            existingAppointment.getDurationMinutes()));
        }

        if (request.getCustomerNotes() != null) {
            existingAppointment.setCustomerNotes(request.getCustomerNotes());
        }

        if (request.getInternalNotes() != null) {
            existingAppointment.setInternalNotes(request.getInternalNotes());
        }

        validateNoOverlap(existingAppointment.getStaffMemberId(),
                existingAppointment.getStartsAt(),
                existingAppointment.getDurationMinutes(),
                id,
                businessId
        );

        existingAppointment.setUpdatedAt(LocalDateTime.now());
        Appointment updatedAppointment = appointmentRepository.save(existingAppointment);
        log.info("Appointment with id={} businessId={} successfully updated.", id, businessId);

        return appointmentMapper.toResponseDto(updatedAppointment);
    }

    @Override
    public void deleteAppointment(Long id) {
        Long businessId = currentBusinessContext.getCurrentBusinessId();

        Appointment appointment = appointmentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with ID: " + id));

        appointmentRepository.delete(appointment);

        log.info("Appointment with id={} businessId={} successfully deleted.", id, businessId);
    }
}
