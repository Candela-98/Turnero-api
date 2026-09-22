package com.turnero.api.service;

import com.turnero.api.dto.*;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.*;
import com.turnero.api.model.enums.*;
import com.turnero.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PublicBookingServiceImpl implements PublicBookingService{

    private final BusinessRepository businessRepository;
    private final BookingSettingsRepository bookingSettingsRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final StaffServiceOfferingRepository staffServiceOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final AvailabilityService availabilityService;
    private final CustomerRepository customerRepository;
    private final AppointmentPublicTokenRepository appointmentPublicTokenRepository;
    private final AppointmentRepository appointmentRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public PublicBookingProfileResponseDto getPublicBookingProfile(String businessSlug) {

        PublicBookingContext context = resolvePublicBookingContext(businessSlug);

        Business business = context.business();
        BookingSettings bookingSettings = context.bookingSettings();

        PublicBusinessResponseDto publicBusiness = PublicBusinessResponseDto.builder()
                .name(business.getName())
                .slug(business.getSlug())
                .industry(business.getIndustry())
                .timezone(business.getTimezone())
                .build();

        PublicBookingSettingsResponseDto publicBookingSettings =
                PublicBookingSettingsResponseDto.builder()
                        .isPublicBookingEnabled(bookingSettings.isPublicBookingEnabled())
                        .bookingWindowDays(bookingSettings.getBookingWindowDays())
                        .minNoticeHours(bookingSettings.getMinNoticeHours())
                        .slotIntervalMinutes(bookingSettings.getSlotIntervalMinutes())
                        .manualConfirmationEnabled(bookingSettings.isManualConfirmationEnabled())
                        .build();

        return PublicBookingProfileResponseDto.builder()
                .business(publicBusiness)
                .bookingSettings(publicBookingSettings)
                .build();
    }

    @Override
    public PublicServiceOfferingListResponseDto getPublicServices(String businessSlug) {

        PublicBookingContext context = resolvePublicBookingContext(businessSlug);
        Business business = context.business();

        List<ServiceOffering> services = servOfferingRepository.findByBusinessId(business.getId());

        List<PublicServiceOfferingResponseDto> publicServices = services.stream()
                .filter(service -> service.getStatus() == ServiceOfferingStatus.ACTIVE)
                .map(service -> {

                    List<Long> staffIds = staffServiceOfferingRepository
                            .findAllByServiceOfferingId(service.getId())
                            .stream()
                            .map(relation -> relation.getStaffMemberId())
                            .toList();

                    if (staffIds.isEmpty()) {
                        return null;
                    }

                    List<PublicStaffMemberResponseDto> activeStaff = staffMemberRepository
                                    .findAllByIdInAndBusinessId(staffIds, business.getId())
                                    .stream()
                                    .filter(staff -> staff.getStatus() == StaffMemberStatus.ACTIVE)
                                    .map(staff -> PublicStaffMemberResponseDto.builder()
                                                    .id(staff.getId())
                                                    .name(staff.getName())
                                                    .roleLabel(staff.getRoleLabel())
                                                    .specialty(staff.getSpecialty())
                                                    .avatarUrl(staff.getAvatarUrl())
                                                    .build()
                                    )
                                    .toList();

                    if (activeStaff.isEmpty()) {
                        return null;
                    }

                    return PublicServiceOfferingResponseDto.builder()
                            .id(service.getId())
                            .name(service.getName())
                            .category(service.getCategory())
                            .durationMinutes(service.getDurationMinutes())
                            .priceCents(service.getPriceCents())
                            .staffMembers(activeStaff)
                            .build();
                })
                .filter(service -> service != null)
                .toList();

        return PublicServiceOfferingListResponseDto.builder()
                .services(publicServices)
                .build();
    }

    private PublicBookingContext resolvePublicBookingContext(String businessSlug) {

        Business business = businessRepository.findBySlug(businessSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (business.getStatus() != BusinessStatus.ACTIVE) {
            throw new ForbiddenException("Public booking is not available");
        }

        BookingSettings bookingSettings = bookingSettingsRepository
                .findByBusinessId(business.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking settings not found"));

        if (!bookingSettings.isPublicBookingEnabled()) {
            throw new ForbiddenException("Public booking is disabled");
        }

        return new PublicBookingContext(business, bookingSettings);
    }

    private record PublicBookingContext(Business business, BookingSettings bookingSettings) {
    }

    @Override
    public List<PublicAvailabilitySlotResponseDto> getPublicAvailability(String businessSlug, LocalDate from,
            LocalDate to, Long serviceOfferingId, String staffMemberId) {

        PublicBookingContext context = resolvePublicBookingContext(businessSlug);

        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required.");
        }

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to.");
        }

        ZoneId businessZone = ZoneId.of(context.business().getTimezone());

        LocalDate today = LocalDate.now(businessZone);

        int bookingWindowDays = context.bookingSettings().getBookingWindowDays();

        if (bookingWindowDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking window must be greater than zero");
        }

        LocalDate maxBookingDate = today.plusDays(bookingWindowDays - 1L);

        if (from.isBefore(today) || to.isAfter(maxBookingDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested dates are outside the public booking window");
        }

        int minNoticeHours = context.bookingSettings().getMinNoticeHours();

        if (minNoticeHours < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum notice hours cannot be negative");
        }

        LocalDateTime minimumAllowedStart = LocalDateTime.now(businessZone).plusHours(minNoticeHours);

        if ("any".equalsIgnoreCase(staffMemberId)) {

            return getAvailabilityForAnyStaff(context.business().getId(), from, to, serviceOfferingId).stream()
                    .filter(slot -> !slot.getStartsAt().isBefore(minimumAllowedStart))
                    .toList();
        }

        Long resolvedStaffMemberId;

        try {
            resolvedStaffMemberId = Long.valueOf(staffMemberId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "staff_member_id must be a numeric ID or 'any'");
        }

        var staffMember = staffMemberRepository
                .findByIdAndBusinessId(resolvedStaffMemberId, context.business().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found."));

        if (staffMember.getStatus() != StaffMemberStatus.ACTIVE) {
            throw new ResourceNotFoundException("Staff member not found.");
        }

        boolean offersService = staffServiceOfferingRepository
                .findAllByStaffMemberId(resolvedStaffMemberId)
                .stream()
                .anyMatch(relation -> relation.getServiceOfferingId().equals(serviceOfferingId));

        if (!offersService) {
            throw new ResourceNotFoundException("Staff member does not offer this service.");
        }

        var slots = availabilityService.getAvailableSlotsForBusiness(context.business().getId(),
                        from,
                        to,
                        serviceOfferingId,
                        resolvedStaffMemberId,
                        null
                );

        return slots.stream()
                .filter(slot -> !slot.getStartsAt().isBefore(minimumAllowedStart))
                .map(slot -> PublicAvailabilitySlotResponseDto.builder()
                                .startsAt(slot.getStartsAt())
                                .endsAt(slot.getEndsAt())
                                .availableStaffMemberIds(List.of(resolvedStaffMemberId))
                                .build()
                )
                .toList();
    }

    private List<PublicAvailabilitySlotResponseDto> getAvailabilityForAnyStaff(Long businessId, LocalDate from, LocalDate to, Long serviceOfferingId) {
        var staffIds = staffServiceOfferingRepository
                .findAllByServiceOfferingId(serviceOfferingId)
                .stream()
                .map(relation -> relation.getStaffMemberId())
                .toList();

        var activeStaff = staffMemberRepository
                .findAllByIdInAndBusinessId(staffIds, businessId)
                .stream()
                .filter(staff -> staff.getStatus() == StaffMemberStatus.ACTIVE)
                .toList();

        var availabilityBySlot = new LinkedHashMap<String, PublicAvailabilitySlotResponseDto>();

        for (var staff : activeStaff) {

            Long currentStaffMemberId = staff.getId();

            var slots = availabilityService.getAvailableSlotsForBusiness(
                            businessId,
                            from,
                            to,
                            serviceOfferingId,
                            currentStaffMemberId,
                            null
                    );

            for (var slot : slots) {

                String key = slot.getStartsAt() + "|" + slot.getEndsAt();

                var existing = availabilityBySlot.get(key);

                if (existing == null) {
                    availabilityBySlot.put(key, PublicAvailabilitySlotResponseDto.builder()
                                    .startsAt(slot.getStartsAt())
                                    .endsAt(slot.getEndsAt())
                                    .availableStaffMemberIds(new ArrayList<>(List.of(currentStaffMemberId)))
                                    .build()
                    );

                } else {
                    existing.getAvailableStaffMemberIds().add(currentStaffMemberId);
                }
            }
        }

        return new ArrayList<>(availabilityBySlot.values());
    }

    private Customer findOrCreateCustomer(Long businessId, PublicAppointmentCustomerRequestDto customerRequest) {
        String email = customerRequest.getEmail();
        String phoneNumber = customerRequest.getPhoneNumber();

        if ((email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer email or phone number is required");
        }

        Optional<Customer> existingCustomer;

        if (email != null && !email.isBlank()) {
            existingCustomer = customerRepository.findByBusinessIdAndEmailIgnoreCase(businessId, email.trim());
        } else {
            existingCustomer = customerRepository.findByBusinessIdAndPhoneNumber(businessId, phoneNumber.trim());
        }

        if (existingCustomer.isPresent()) {
            return existingCustomer.get();
        }

        LocalDateTime now = LocalDateTime.now();

        Customer customer = Customer.builder()
                .businessId(businessId)
                .userId(null)
                .name(customerRequest.getName().trim())
                .email(email == null ? null : email.trim())
                .phoneNumber(phoneNumber == null ? null : phoneNumber.trim())
                .status(CustomerStatus.ACTIVE)
                .internalNotes(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return customerRepository.save(customer);
    }

    private ServiceOffering getActiveServiceOffering(Long businessId, Long serviceOfferingId) {
        ServiceOffering serviceOffering = servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Service offering not found with ID: " + serviceOfferingId));

        if (serviceOffering.getStatus() != ServiceOfferingStatus.ACTIVE) {
            throw new ForbiddenException("Service offering is not active");
        }

        return serviceOffering;
    }

    private boolean isSlotAvailableForStaff(Long businessId, Long serviceOfferingId, Long staffMemberId, LocalDateTime startsAt) {

        List<AvailabilitySlotResponseDto> availableSlots = availabilityService.getAvailableSlotsForBusiness(businessId,
                        startsAt.toLocalDate(),
                        startsAt.toLocalDate(),
                        serviceOfferingId,
                        staffMemberId,
                        null
                );

        return availableSlots.stream()
                .anyMatch(slot -> slot.getStartsAt().equals(startsAt));
    }

    private StaffMember resolveSpecificStaffMember(Long businessId, Long serviceOfferingId, Long staffMemberId, LocalDateTime startsAt) {
        StaffMember staffMember = staffMemberRepository.findByIdAndBusinessId(staffMemberId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + staffMemberId));

        if (staffMember.getStatus() != StaffMemberStatus.ACTIVE) {
            throw new ForbiddenException("Staff member is not active");
        }

        boolean offersService = staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId)
                .stream()
                .anyMatch(assignment -> assignment.getStaffMemberId().equals(staffMemberId));

        if (!offersService) {
            throw new ForbiddenException("Staff member does not offer the selected service");
        }

        if (!isSlotAvailableForStaff(businessId, serviceOfferingId, staffMemberId, startsAt)) {
            throw new AppointmentOverlapException("The selected slot is not available");
        }

        return staffMember;
    }

    private StaffMember resolveAnyStaffMember(Long businessId, Long serviceOfferingId, LocalDateTime startsAt) {
        List<StaffServiceOffering> assignments = staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId);

        List<Long> staffMemberIds = assignments.stream()
                .map(StaffServiceOffering::getStaffMemberId)
                .toList();

        List<StaffMember> staffMembers = staffMemberRepository.findAllByIdInAndBusinessId(staffMemberIds, businessId);

        return staffMembers.stream()
                .filter(staffMember -> staffMember.getStatus() == StaffMemberStatus.ACTIVE)
                .filter(staffMember -> isSlotAvailableForStaff(
                                businessId,
                                serviceOfferingId,
                                staffMember.getId(),
                                startsAt
                        )
                )
                .findFirst()
                .orElseThrow(() -> new AppointmentOverlapException("The selected slot is not available"));
    }

    private StaffMember resolveStaffMember(Long businessId, Long serviceOfferingId, String staffMemberId, LocalDateTime startsAt) {
        String normalizedStaffMemberId = staffMemberId.trim();

        if ("any".equalsIgnoreCase(normalizedStaffMemberId)) {
            return resolveAnyStaffMember(businessId, serviceOfferingId, startsAt);
        }

        Long parsedStaffMemberId;

        try {
            parsedStaffMemberId = Long.valueOf(normalizedStaffMemberId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff member ID must be a valid number or 'any'");
        }

        return resolveSpecificStaffMember(businessId, serviceOfferingId, parsedStaffMemberId, startsAt);
    }

    private void validatePublicBookingDateTime(Business business, BookingSettings bookingSettings, LocalDateTime startsAt) {

        ZoneId businessZone = ZoneId.of(business.getTimezone());

        LocalDate today = LocalDate.now(businessZone);

        int bookingWindowDays = bookingSettings.getBookingWindowDays();

        if (bookingWindowDays <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking window must be greater than zero");
        }

        LocalDate maxBookingDate = today.plusDays(bookingWindowDays - 1L);

        LocalDate appointmentDate = startsAt.toLocalDate();

        if (appointmentDate.isBefore(today) || appointmentDate.isAfter(maxBookingDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested date is outside the public booking window");
        }

        int minNoticeHours = bookingSettings.getMinNoticeHours();

        if (minNoticeHours < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum notice hours cannot be negative");
        }

        LocalDateTime minimumAllowedStart = LocalDateTime.now(businessZone).plusHours(minNoticeHours);

        if (startsAt.isBefore(minimumAllowedStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment does not meet the minimum notice requirement");
        }
    }

    private String generatePublicToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashPublicToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String createCancellationToken(Appointment appointment) {
        String plainToken = generatePublicToken();
        String tokenHash = hashPublicToken(plainToken);

        AppointmentPublicToken publicToken = AppointmentPublicToken.builder()
                .appointmentId(appointment.getId())
                .tokenHash(tokenHash)
                .type(AppointmentPublicTokenType.CANCEL)
                .expiresAt(appointment.getStartsAt())
                .usedAt(null)
                .createdAt(LocalDateTime.now())
                .build();

        appointmentPublicTokenRepository.save(publicToken);

        return plainToken;
    }

    private StaffMember lockAndRevalidateStaffMember(Long businessId, Long serviceOfferingId, Long staffMemberId, LocalDateTime startsAt) {

        StaffMember lockedStaffMember = staffMemberRepository.findByIdAndBusinessIdForUpdate(staffMemberId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with ID: " + staffMemberId));

        if (!isSlotAvailableForStaff(businessId, serviceOfferingId, lockedStaffMember.getId(), startsAt)) {
            throw new AppointmentOverlapException("The selected slot is not available");
        }

        return lockedStaffMember;
    }

    @Transactional
    @Override
    public PublicAppointmentResponseDto createPublicAppointment(String businessSlug, PublicAppointmentRequestDto request) {
        PublicBookingContext context = resolvePublicBookingContext(businessSlug);

        Business business = context.business();
        BookingSettings bookingSettings = context.bookingSettings();

        validatePublicBookingDateTime(business, bookingSettings, request.getStartsAt());

        ServiceOffering serviceOffering = getActiveServiceOffering(business.getId(), request.getServiceOfferingId());

        StaffMember resolvedStaffMember = resolveStaffMember(business.getId(), serviceOffering.getId(),
                        request.getStaffMemberId(), request.getStartsAt());

        StaffMember lockedStaffMember = lockAndRevalidateStaffMember(business.getId(), serviceOffering.getId(),
                        resolvedStaffMember.getId(), request.getStartsAt());

        Customer customer = findOrCreateCustomer(business.getId(), request.getCustomer());

        Appointment appointment = buildPublicAppointment(business, customer, serviceOffering, lockedStaffMember, request, bookingSettings);

        Appointment savedAppointment = appointmentRepository.save(appointment);

        String cancelToken = createCancellationToken(savedAppointment);

        return buildPublicAppointmentResponse(savedAppointment, cancelToken);
    }

    //region Private Helper Methods
    private Appointment buildPublicAppointment(Business business, Customer customer, ServiceOffering serviceOffering,
            StaffMember staffMember, PublicAppointmentRequestDto request, BookingSettings bookingSettings) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime endsAt = request.getStartsAt().plusMinutes(serviceOffering.getDurationMinutes());

        AppointmentStatus status = bookingSettings.isManualConfirmationEnabled()
                        ? AppointmentStatus.PENDING
                        : AppointmentStatus.CONFIRMED;

        return Appointment.builder()
                .businessId(business.getId())
                .customerId(customer.getId())
                .serviceOfferingId(serviceOffering.getId())
                .staffMemberId(staffMember.getId())
                .startsAt(request.getStartsAt())
                .endsAt(endsAt)
                .durationMinutes(serviceOffering.getDurationMinutes())
                .priceCents(serviceOffering.getPriceCents())
                .status(status)
                .source(AppointmentSource.PUBLIC_BOOKING)
                .customerNotes(request.getCustomerNotes())
                .internalNotes(null)
                .cancellationReason(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private PublicAppointmentResponseDto buildPublicAppointmentResponse(Appointment appointment, String cancelToken) {
        return PublicAppointmentResponseDto.builder()
                .appointmentId(appointment.getId())
                .serviceOfferingId(appointment.getServiceOfferingId())
                .staffMemberId(appointment.getStaffMemberId())
                .startsAt(appointment.getStartsAt())
                .endsAt(appointment.getEndsAt())
                .durationMinutes(appointment.getDurationMinutes())
                .priceCents(appointment.getPriceCents())
                .status(appointment.getStatus())
                .cancelToken(cancelToken)
                .build();
    }

    //endregion
}
