package com.turnero.api.service;

import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.model.Business;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.StaffServiceOffering;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.repository.BookingSettingsRepository;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffServiceOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicBookingServiceImplTest {

    private static final String BUSINESS_SLUG = "barber-studio";
    private static final Long BUSINESS_ID = 1L;

    @Mock private BusinessRepository businessRepository;
    @Mock private BookingSettingsRepository bookingSettingsRepository;
    @Mock private ServOfferingRepository servOfferingRepository;
    @Mock private StaffServiceOfferingRepository staffServiceOfferingRepository;
    @Mock private StaffMemberRepository staffMemberRepository;

    @InjectMocks private PublicBookingServiceImpl publicBookingService;

    @Test
    void getPublicBookingProfile_whenBusinessIsActiveAndBookingEnabled_returnsPublicProfile() {
        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(enabledSettings()));

        PublicBookingProfileResponseDto response = publicBookingService.getPublicBookingProfile(BUSINESS_SLUG);

        assertThat(response.getBusiness().getName()).isEqualTo("Barber Studio");
        assertThat(response.getBusiness().getSlug()).isEqualTo(BUSINESS_SLUG);
        assertThat(response.getBusiness().getIndustry()).isEqualTo("Barber");
        assertThat(response.getBusiness().getTimezone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(response.getBookingSettings().isPublicBookingEnabled()).isTrue();
        assertThat(response.getBookingSettings().getBookingWindowDays()).isEqualTo(7);
        assertThat(response.getBookingSettings().getMinNoticeHours()).isEqualTo(3);
        assertThat(response.getBookingSettings().getSlotIntervalMinutes()).isEqualTo(30);
        assertThat(response.getBookingSettings().isManualConfirmationEnabled()).isTrue();
    }

    @Test
    void getPublicBookingProfile_whenSlugDoesNotExist_throwsNotFound() {
        given(businessRepository.findBySlug("missing-business")).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicBookingService.getPublicBookingProfile("missing-business"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Business not found");

        verify(bookingSettingsRepository, never()).findByBusinessId(BUSINESS_ID);
    }

    @Test
    void getPublicBookingProfile_whenBookingIsDisabled_throwsForbidden() {
        BookingSettings settings = enabledSettings();
        settings.setPublicBookingEnabled(false);
        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));

        assertThatThrownBy(() -> publicBookingService.getPublicBookingProfile(BUSINESS_SLUG))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Public booking is disabled");
    }

    @Test
    void getPublicBookingProfile_whenBusinessIsInactive_throwsForbidden() {
        Business business = activeBusiness();
        business.setStatus(BusinessStatus.INACTIVE);
        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(business));

        assertThatThrownBy(() -> publicBookingService.getPublicBookingProfile(BUSINESS_SLUG))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Public booking is not available");

        verify(bookingSettingsRepository, never()).findByBusinessId(BUSINESS_ID);
    }

    @Test
    void getPublicServices_excludesServicesWithoutActiveStaffAndInactiveServices() {
        ServiceOffering activeWithActiveStaff = service(10L, "Haircut", ServiceOfferingStatus.ACTIVE);
        ServiceOffering activeWithoutStaff = service(20L, "Beard Trim", ServiceOfferingStatus.ACTIVE);
        ServiceOffering activeWithInactiveStaff = service(30L, "Color", ServiceOfferingStatus.ACTIVE);
        ServiceOffering inactiveService = service(40L, "Shave", ServiceOfferingStatus.INACTIVE);
        StaffMember activeStaff = staff(100L, "John Doe", StaffMemberStatus.ACTIVE);
        StaffMember inactiveStaff = staff(200L, "Jane Doe", StaffMemberStatus.INACTIVE);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(enabledSettings()));
        given(servOfferingRepository.findByBusinessId(BUSINESS_ID)).willReturn(List.of(
                activeWithActiveStaff,
                activeWithoutStaff,
                activeWithInactiveStaff,
                inactiveService
        ));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(10L))
                .willReturn(List.of(relation(100L, 10L)));
        given(staffMemberRepository.findAllByIdInAndBusinessId(List.of(100L), BUSINESS_ID))
                .willReturn(List.of(activeStaff));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(20L))
                .willReturn(List.of());
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(30L))
                .willReturn(List.of(relation(200L, 30L)));
        given(staffMemberRepository.findAllByIdInAndBusinessId(List.of(200L), BUSINESS_ID))
                .willReturn(List.of(inactiveStaff));

        PublicServiceOfferingListResponseDto response = publicBookingService.getPublicServices(BUSINESS_SLUG);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().getId()).isEqualTo(10L);
        assertThat(response.getData().getFirst().getName()).isEqualTo("Haircut");
        assertThat(response.getData().getFirst().getStaffMembers()).hasSize(1);
        assertThat(response.getData().getFirst().getStaffMembers().getFirst().getId()).isEqualTo(100L);
        assertThat(response.getData().getFirst().getStaffMembers().getFirst().getName()).isEqualTo("John Doe");

        verify(staffServiceOfferingRepository, never()).findAllByServiceOfferingId(40L);
    }

    private Business activeBusiness() {
        return Business.builder()
                .id(BUSINESS_ID)
                .name("Barber Studio")
                .slug(BUSINESS_SLUG)
                .industry("Barber")
                .timezone("America/Argentina/Buenos_Aires")
                .status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.READY)
                .build();
    }

    private BookingSettings enabledSettings() {
        return BookingSettings.builder()
                .id(1L)
                .businessId(BUSINESS_ID)
                .publicBookingEnabled(true)
                .requiresCustomerLogin(false)
                .bookingWindowDays(7)
                .minNoticeHours(3)
                .cancellationNoticeHours(24)
                .slotIntervalMinutes(30)
                .manualConfirmationEnabled(true)
                .whatsappRemindersEnabled(true)
                .build();
    }

    private ServiceOffering service(Long id, String name, ServiceOfferingStatus status) {
        return ServiceOffering.builder()
                .id(id)
                .businessId(BUSINESS_ID)
                .name(name)
                .category("Hair Services")
                .durationMinutes(30)
                .priceCents(25000)
                .status(status)
                .build();
    }

    private StaffMember staff(Long id, String name, StaffMemberStatus status) {
        return StaffMember.builder()
                .id(id)
                .businessId(BUSINESS_ID)
                .name(name)
                .roleLabel("Barber")
                .specialty("Haircut")
                .avatarUrl("https://example.com/avatar.jpg")
                .status(status)
                .build();
    }

    private StaffServiceOffering relation(Long staffMemberId, Long serviceOfferingId) {
        return StaffServiceOffering.builder()
                .staffMemberId(staffMemberId)
                .serviceOfferingId(serviceOfferingId)
                .build();
    }
}
