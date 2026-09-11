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
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicBookingServiceImpl implements PublicBookingService{

    private final BusinessRepository businessRepository;
    private final BookingSettingsRepository bookingSettingsRepository;
    private final ServOfferingRepository servOfferingRepository;
    private final StaffServiceOfferingRepository staffServiceOfferingRepository;
    private final StaffMemberRepository staffMemberRepository;


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

    private record PublicBookingContext(
            Business business,
            BookingSettings bookingSettings
    ) {
    }
}
