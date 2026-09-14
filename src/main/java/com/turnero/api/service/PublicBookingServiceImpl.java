package com.turnero.api.service;

import com.turnero.api.dto.*;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.model.Business;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicBookingServiceImpl implements PublicBookingService{

    private final BusinessRepository businessRepository;
    private final BookingSettingsRepository bookingSettingsRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final StaffServiceOfferingRepository staffServiceOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final AvailabilityService availabilityService;


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

        var slots = availabilityService.getAvailableSlotsForBusiness(
                        context.business().getId(),
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

    private List<PublicAvailabilitySlotResponseDto> getAvailabilityForAnyStaff(
            Long businessId,
            LocalDate from,
            LocalDate to,
            Long serviceOfferingId) {

        var staffIds = staffServiceOfferingRepository
                .findAllByServiceOfferingId(serviceOfferingId)
                .stream()
                .map(relation -> relation.getStaffMemberId())
                .toList();

        var activeStaff = staffMemberRepository
                .findAllByIdInAndBusinessId(staffIds, businessId)
                .stream()
                .filter(staff ->
                        staff.getStatus() == StaffMemberStatus.ACTIVE
                )
                .toList();

        var availabilityBySlot =
                new LinkedHashMap<String, PublicAvailabilitySlotResponseDto>();

        for (var staff : activeStaff) {

            Long currentStaffMemberId = staff.getId();

            var slots = availabilityService
                    .getAvailableSlotsForBusiness(
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
}
