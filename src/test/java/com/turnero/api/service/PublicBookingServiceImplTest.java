package com.turnero.api.service;

import com.turnero.api.dto.AvailabilitySlotResponseDto;
import com.turnero.api.dto.PublicAppointmentCustomerRequestDto;
import com.turnero.api.dto.PublicAppointmentRequestDto;
import com.turnero.api.dto.PublicAppointmentResponseDto;
import com.turnero.api.dto.PublicAvailabilitySlotResponseDto;
import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;
import com.turnero.api.exception.AppointmentOverlapException;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.AppointmentPublicToken;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.model.Business;
import com.turnero.api.model.Customer;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.StaffMember;
import com.turnero.api.model.StaffServiceOffering;
import com.turnero.api.model.enums.AppointmentPublicTokenType;
import com.turnero.api.model.enums.AppointmentSource;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.CustomerStatus;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.model.enums.StaffMemberStatus;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.BookingSettingsRepository;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.StaffMemberRepository;
import com.turnero.api.repository.StaffServiceOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private AvailabilityService availabilityService;
    @Mock private CustomerRepository customerRepository;
    @Mock private AppointmentPublicTokenRepository appointmentPublicTokenRepository;
    @Mock private AppointmentRepository appointmentRepository;

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
        given(staffMemberRepository.findAllByIdInAndBusinessIdAndStatus(List.of(100L), BUSINESS_ID,
                StaffMemberStatus.ACTIVE))
                .willReturn(List.of(activeStaff));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(20L))
                .willReturn(List.of());
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(30L))
                .willReturn(List.of(relation(200L, 30L)));
        given(staffMemberRepository.findAllByIdInAndBusinessIdAndStatus(List.of(200L), BUSINESS_ID,
                StaffMemberStatus.ACTIVE))
                .willReturn(List.of());

        PublicServiceOfferingListResponseDto response = publicBookingService.getPublicServices(BUSINESS_SLUG);

        assertThat(response.getServices()).hasSize(1);
        assertThat(response.getServices().getFirst().getId()).isEqualTo(10L);
        assertThat(response.getServices().getFirst().getName()).isEqualTo("Haircut");
        assertThat(response.getServices().getFirst().getStaffMembers()).hasSize(1);
        assertThat(response.getServices().getFirst().getStaffMembers().getFirst().getId()).isEqualTo(100L);
        assertThat(response.getServices().getFirst().getStaffMembers().getFirst().getName()).isEqualTo("John Doe");

        verify(staffServiceOfferingRepository, never()).findAllByServiceOfferingId(40L);
    }

    @Test
    void getPublicAvailability_whenSpecificStaffOffersService_returnsSlotsWithStaffId() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate from = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDate to = from;
        LocalDateTime startsAt = from.atTime(10, 0);
        LocalDateTime endsAt = from.atTime(10, 30);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId))
                .willReturn(List.of(relation(staffMemberId, serviceOfferingId)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, from, to, serviceOfferingId, staffMemberId, null))
                .willReturn(List.of(slot(startsAt, endsAt)));

        List<PublicAvailabilitySlotResponseDto> response = publicBookingService.getPublicAvailability(
                BUSINESS_SLUG, from, to, serviceOfferingId, staffMemberId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getStartsAt()).isEqualTo(startsAt);
        assertThat(response.getFirst().getEndsAt()).isEqualTo(endsAt);
        assertThat(response.getFirst().getAvailableStaffMemberIds()).containsExactly(staffMemberId);

        verify(availabilityService).getAvailableSlotsForBusiness(
                BUSINESS_ID, from, to, serviceOfferingId, staffMemberId, null);
    }

    @Test
    void getPublicAvailability_whenAnyStaffRequested_groupsSameSlotAndUsesOnlyActiveBusinessStaffIds() {
        Long serviceOfferingId = 10L;
        Long firstStaffId = 100L;
        Long secondStaffId = 200L;
        Long inactiveStaffId = 300L;
        LocalDate from = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDate to = from;
        LocalDateTime sharedStart = from.atTime(10, 0);
        LocalDateTime sharedEnd = from.atTime(10, 30);
        LocalDateTime secondStart = from.atTime(11, 0);
        LocalDateTime secondEnd = from.atTime(11, 30);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId))
                .willReturn(List.of(
                        relation(firstStaffId, serviceOfferingId),
                        relation(secondStaffId, serviceOfferingId),
                        relation(inactiveStaffId, serviceOfferingId)));
        given(staffMemberRepository.findAllByIdInAndBusinessIdAndStatus(
                List.of(firstStaffId, secondStaffId, inactiveStaffId), BUSINESS_ID, StaffMemberStatus.ACTIVE))
                .willReturn(List.of(
                        staff(firstStaffId, "John Doe", StaffMemberStatus.ACTIVE),
                        staff(secondStaffId, "Jane Doe", StaffMemberStatus.ACTIVE)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, from, to, serviceOfferingId, firstStaffId, null))
                .willReturn(List.of(slot(sharedStart, sharedEnd), slot(secondStart, secondEnd)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, from, to, serviceOfferingId, secondStaffId, null))
                .willReturn(List.of(slot(sharedStart, sharedEnd)));

        List<PublicAvailabilitySlotResponseDto> response = publicBookingService.getPublicAvailability(
                BUSINESS_SLUG, from, to, serviceOfferingId, null);

        assertThat(response).hasSize(2);
        assertThat(response.getFirst().getStartsAt()).isEqualTo(sharedStart);
        assertThat(response.getFirst().getEndsAt()).isEqualTo(sharedEnd);
        assertThat(response.getFirst().getAvailableStaffMemberIds()).containsExactly(firstStaffId, secondStaffId);
        assertThat(response.get(1).getStartsAt()).isEqualTo(secondStart);
        assertThat(response.get(1).getEndsAt()).isEqualTo(secondEnd);
        assertThat(response.get(1).getAvailableStaffMemberIds()).containsExactly(firstStaffId);

        verify(availabilityService).getAvailableSlotsForBusiness(
                BUSINESS_ID, from, to, serviceOfferingId, firstStaffId, null);
        verify(availabilityService).getAvailableSlotsForBusiness(
                BUSINESS_ID, from, to, serviceOfferingId, secondStaffId, null);
        verify(availabilityService, never()).getAvailableSlotsForBusiness(
                eq(BUSINESS_ID), eq(from), eq(to), eq(serviceOfferingId), eq(inactiveStaffId), isNull());
    }

    @Test
    void getPublicAvailability_whenAnyStaffRequestedAndServiceHasNoStaffRelations_returnsEmptyAvailability() {
        Long serviceOfferingId = 10L;
        LocalDate from = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId))
                .willReturn(List.of());

        List<PublicAvailabilitySlotResponseDto> response = publicBookingService.getPublicAvailability(
                BUSINESS_SLUG, from, from, serviceOfferingId, null);

        assertThat(response).isEmpty();

        verify(staffMemberRepository, never()).findAllByIdInAndBusinessIdAndStatus(
                org.mockito.ArgumentMatchers.anyList(),
                eq(BUSINESS_ID),
                eq(StaffMemberStatus.ACTIVE));
        verify(availabilityService, never()).getAvailableSlotsForBusiness(
                eq(BUSINESS_ID), eq(from), eq(from), eq(serviceOfferingId), org.mockito.ArgumentMatchers.any(),
                isNull());
    }

    @Test
    void getPublicAvailability_whenSpecificStaffDoesNotOfferService_throwsNotFound() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate from = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(enabledSettings()));
        given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId))
                .willReturn(List.of(relation(staffMemberId, 99L)));

        assertThatThrownBy(() -> publicBookingService.getPublicAvailability(
                BUSINESS_SLUG, from, from, serviceOfferingId, staffMemberId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Staff member does not offer this service.");

        verify(availabilityService, never()).getAvailableSlotsForBusiness(
                eq(BUSINESS_ID), eq(from), eq(from), eq(serviceOfferingId), eq(staffMemberId), isNull());
    }

    @Test
    void getPublicAvailability_appliesMinNoticeHoursInBusinessTimezoneAndKeepsBoundarySlot() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        ZoneId businessZone = ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate fixedToday = LocalDate.of(2026, 9, 14);
        LocalDate from = fixedToday;
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime boundary = fixedNow.plusHours(3);
        LocalDateTime tooSoonStartsAt = boundary.minusMinutes(1);
        LocalDateTime tooSoonEndsAt = boundary.plusMinutes(29);
        LocalDateTime boundaryEndsAt = boundary.plusMinutes(30);

        try (MockedStatic<LocalDate> localDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDate.when(() -> LocalDate.now(businessZone)).thenReturn(fixedToday);
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(fixedNow);

            BookingSettings settings = enabledSettings();
            settings.setMinNoticeHours(3);
            given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
            given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
            given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                    .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
            given(staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId))
                    .willReturn(List.of(relation(staffMemberId, serviceOfferingId)));
            given(availabilityService.getAvailableSlotsForBusiness(
                    BUSINESS_ID, from, from, serviceOfferingId, staffMemberId, null))
                    .willReturn(List.of(
                            slot(tooSoonStartsAt, tooSoonEndsAt),
                            slot(boundary, boundaryEndsAt)));

            List<PublicAvailabilitySlotResponseDto> response = publicBookingService.getPublicAvailability(
                    BUSINESS_SLUG, from, from, serviceOfferingId, staffMemberId);

            assertThat(response).hasSize(1);
            assertThat(response.getFirst().getStartsAt()).isEqualTo(boundary);
            assertThat(response.getFirst().getEndsAt()).isEqualTo(boundaryEndsAt);
            assertThat(response.getFirst().getAvailableStaffMemberIds()).containsExactly(staffMemberId);
        }
    }

    @Test
    void getPublicAvailability_allowsDateInsideBookingWindow() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        ZoneId businessZone = ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate fixedToday = LocalDate.of(2026, 9, 14);
        LocalDate requestedDate = fixedToday.plusDays(3);
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime startsAt = requestedDate.atTime(10, 0);
        LocalDateTime endsAt = startsAt.plusMinutes(30);

        try (MockedStatic<LocalDate> localDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDate.when(() -> LocalDate.now(businessZone)).thenReturn(fixedToday);
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(fixedNow);

            givenSuccessfulSpecificStaffAvailability(serviceOfferingId, staffMemberId, requestedDate, startsAt,
                    endsAt);

            List<PublicAvailabilitySlotResponseDto> response = publicBookingService.getPublicAvailability(
                    BUSINESS_SLUG, requestedDate, requestedDate, serviceOfferingId, staffMemberId);

            assertThat(response).hasSize(1);
        }
    }

    @Test
    void getPublicAvailability_allowsLastDateInsideBookingWindowWhenTodayCountsAsDayOne() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        ZoneId businessZone = ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate fixedToday = LocalDate.of(2026, 9, 14);
        LocalDate lastAllowedDate = fixedToday.plusDays(6);
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);
        LocalDateTime startsAt = lastAllowedDate.atTime(10, 0);
        LocalDateTime endsAt = startsAt.plusMinutes(30);

        try (MockedStatic<LocalDate> localDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDate.when(() -> LocalDate.now(businessZone)).thenReturn(fixedToday);
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(fixedNow);

            givenSuccessfulSpecificStaffAvailability(serviceOfferingId, staffMemberId, lastAllowedDate, startsAt,
                    endsAt);

            List<PublicAvailabilitySlotResponseDto> response = publicBookingService.getPublicAvailability(
                    BUSINESS_SLUG, lastAllowedDate, lastAllowedDate, serviceOfferingId, staffMemberId);

            assertThat(response).hasSize(1);
        }
    }

    @Test
    void getPublicAvailability_rejectsDateBeforeToday() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        ZoneId businessZone = ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate fixedToday = LocalDate.of(2026, 9, 14);
        LocalDate yesterday = fixedToday.minusDays(1);
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);

        try (MockedStatic<LocalDate> localDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDate.when(() -> LocalDate.now(businessZone)).thenReturn(fixedToday);
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(fixedNow);
            given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
            given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(enabledSettings()));

            assertThatThrownBy(() -> publicBookingService.getPublicAvailability(
                    BUSINESS_SLUG, yesterday, yesterday, serviceOfferingId, staffMemberId))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Requested dates are outside the public booking window");

            verify(availabilityService, never()).getAvailableSlotsForBusiness(
                    eq(BUSINESS_ID), eq(yesterday), eq(yesterday), eq(serviceOfferingId), eq(staffMemberId), isNull());
        }
    }

    @Test
    void getPublicAvailability_rejectsDateAfterLastBookingWindowDay() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        ZoneId businessZone = ZoneId.of("America/Argentina/Buenos_Aires");
        LocalDate fixedToday = LocalDate.of(2026, 9, 14);
        LocalDate dayAfterLastAllowedDate = fixedToday.plusDays(7);
        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 14, 9, 0);

        try (MockedStatic<LocalDate> localDate = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> localDateTime =
                     Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            localDate.when(() -> LocalDate.now(businessZone)).thenReturn(fixedToday);
            localDateTime.when(() -> LocalDateTime.now(businessZone)).thenReturn(fixedNow);
            given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
            given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(enabledSettings()));

            assertThatThrownBy(() -> publicBookingService.getPublicAvailability(
                    BUSINESS_SLUG, dayAfterLastAllowedDate, dayAfterLastAllowedDate, serviceOfferingId,
                    staffMemberId))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Requested dates are outside the public booking window");

            verify(availabilityService, never()).getAvailableSlotsForBusiness(
                    eq(BUSINESS_ID), eq(dayAfterLastAllowedDate), eq(dayAfterLastAllowedDate),
                    eq(serviceOfferingId), eq(staffMemberId), isNull());
        }
    }

    @Test
    void createPublicAppointment_whenSpecificStaffIsSelected_persistsAppointmentWithSelectedStaffAndBackendSnapshots() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate appointmentDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);
        ServiceOffering serviceOffering = service(serviceOfferingId, "Haircut", ServiceOfferingStatus.ACTIVE);
        serviceOffering.setDurationMinutes(45);
        serviceOffering.setPriceCents(32000);
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);
        PublicAppointmentRequestDto request = appointmentRequest(serviceOfferingId, staffMemberId.toString(),
                startsAt);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, BUSINESS_ID))
                .willReturn(Optional.of(serviceOffering));
        given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId))
                .willReturn(List.of(relation(staffMemberId, serviceOfferingId)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, appointmentDate, appointmentDate, serviceOfferingId, staffMemberId, null))
                .willReturn(List.of(slot(startsAt, startsAt.plusMinutes(45))));
        given(staffMemberRepository.findByIdAndBusinessIdForUpdate(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(customerRepository.findByBusinessIdAndEmailIgnoreCase(BUSINESS_ID, "candela@email.com"))
                .willReturn(Optional.of(customer(500L)));
        given(appointmentRepository.save(any(Appointment.class))).willAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(900L);
            return appointment;
        });

        PublicAppointmentResponseDto response = publicBookingService.createPublicAppointment(BUSINESS_SLUG, request);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Appointment savedAppointment = appointmentCaptor.getValue();

        assertThat(savedAppointment.getStaffMemberId()).isEqualTo(staffMemberId);
        assertThat(savedAppointment.getSource()).isEqualTo(AppointmentSource.PUBLIC_BOOKING);
        assertThat(savedAppointment.getDurationMinutes()).isEqualTo(45);
        assertThat(savedAppointment.getPriceCents()).isEqualTo(32000);
        assertThat(savedAppointment.getEndsAt()).isEqualTo(startsAt.plusMinutes(45));
        assertThat(response.getAppointmentId()).isEqualTo(900L);
        assertThat(response.getStaffMemberId()).isEqualTo(staffMemberId);
        assertThat(response.getDurationMinutes()).isEqualTo(45);
        assertThat(response.getPriceCents()).isEqualTo(32000);
        assertThat(response.getEndsAt()).isEqualTo(startsAt.plusMinutes(45));

        verify(staffMemberRepository).findByIdAndBusinessIdForUpdate(staffMemberId, BUSINESS_ID);
        verify(availabilityService, Mockito.times(2)).getAvailableSlotsForBusiness(
                BUSINESS_ID, appointmentDate, appointmentDate, serviceOfferingId, staffMemberId, null);
    }

    @Test
    void createPublicAppointment_whenAnyStaffIsSelected_persistsRealAvailableStaffMember() {
        Long serviceOfferingId = 10L;
        Long firstStaffId = 100L;
        Long secondStaffId = 200L;
        LocalDate appointmentDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);

        givenSuccessfulCreateBase(serviceOfferingId, settings, startsAt);
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId))
                .willReturn(List.of(
                        relation(firstStaffId, serviceOfferingId),
                        relation(secondStaffId, serviceOfferingId)));
        given(staffMemberRepository.findAllByIdInAndBusinessId(List.of(firstStaffId, secondStaffId), BUSINESS_ID))
                .willReturn(List.of(
                        staff(firstStaffId, "John Doe", StaffMemberStatus.INACTIVE),
                        staff(secondStaffId, "Jane Doe", StaffMemberStatus.ACTIVE)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, appointmentDate, appointmentDate, serviceOfferingId, secondStaffId, null))
                .willReturn(List.of(slot(startsAt, startsAt.plusMinutes(30))));
        given(staffMemberRepository.findByIdAndBusinessIdForUpdate(secondStaffId, BUSINESS_ID))
                .willReturn(Optional.of(staff(secondStaffId, "Jane Doe", StaffMemberStatus.ACTIVE)));
        given(customerRepository.findByBusinessIdAndEmailIgnoreCase(BUSINESS_ID, "candela@email.com"))
                .willReturn(Optional.of(customer(500L)));
        given(appointmentRepository.save(any(Appointment.class))).willAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(900L);
            return appointment;
        });

        PublicAppointmentResponseDto response = publicBookingService.createPublicAppointment(BUSINESS_SLUG,
                appointmentRequest(serviceOfferingId, "any", startsAt));

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Long persistedStaffMemberId = appointmentCaptor.getValue().getStaffMemberId();

        assertThat(persistedStaffMemberId).isEqualTo(secondStaffId);
        assertThat(response.getStaffMemberId()).isEqualTo(persistedStaffMemberId);
        assertThat(response.getStaffMemberId()).isNotNull();
        verify(staffMemberRepository).findByIdAndBusinessIdForUpdate(persistedStaffMemberId, BUSINESS_ID);
    }

    @Test
    void createPublicAppointment_whenManualConfirmationIsEnabled_persistsPendingStatus() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate appointmentDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);
        BookingSettings settings = enabledSettings();
        settings.setManualConfirmationEnabled(true);
        settings.setMinNoticeHours(0);

        givenSuccessfulSpecificStaffCreate(serviceOfferingId, staffMemberId, settings, startsAt);

        PublicAppointmentResponseDto response = publicBookingService.createPublicAppointment(BUSINESS_SLUG,
                appointmentRequest(serviceOfferingId, staffMemberId.toString(), startsAt));

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());

        assertThat(appointmentCaptor.getValue().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void createPublicAppointment_whenManualConfirmationIsDisabled_persistsConfirmedStatus() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate appointmentDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);
        BookingSettings settings = enabledSettings();
        settings.setManualConfirmationEnabled(false);
        settings.setMinNoticeHours(0);

        givenSuccessfulSpecificStaffCreate(serviceOfferingId, staffMemberId, settings, startsAt);

        PublicAppointmentResponseDto response = publicBookingService.createPublicAppointment(BUSINESS_SLUG,
                appointmentRequest(serviceOfferingId, staffMemberId.toString(), startsAt));

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());

        assertThat(appointmentCaptor.getValue().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void createPublicAppointment_whenSlotIsOccupied_throwsConflictAndDoesNotPersistAppointment() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate appointmentDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);

        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, BUSINESS_ID))
                .willReturn(Optional.of(service(serviceOfferingId, "Haircut", ServiceOfferingStatus.ACTIVE)));
        given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId))
                .willReturn(List.of(relation(staffMemberId, serviceOfferingId)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, appointmentDate, appointmentDate, serviceOfferingId, staffMemberId, null))
                .willReturn(List.of());

        assertThatThrownBy(() -> publicBookingService.createPublicAppointment(BUSINESS_SLUG,
                appointmentRequest(serviceOfferingId, staffMemberId.toString(), startsAt)))
                .isInstanceOf(AppointmentOverlapException.class)
                .hasMessage("The selected slot is not available");

        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(appointmentPublicTokenRepository, never()).save(any(AppointmentPublicToken.class));
    }

    @Test
    void createPublicAppointment_generatesCancellationTokenAndStoresOnlySha256Hash() {
        Long serviceOfferingId = 10L;
        Long staffMemberId = 100L;
        LocalDate appointmentDate = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(1);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);

        givenSuccessfulSpecificStaffCreate(serviceOfferingId, staffMemberId, settings, startsAt);

        PublicAppointmentResponseDto response = publicBookingService.createPublicAppointment(BUSINESS_SLUG,
                appointmentRequest(serviceOfferingId, staffMemberId.toString(), startsAt));

        ArgumentCaptor<AppointmentPublicToken> tokenCaptor = ArgumentCaptor.forClass(AppointmentPublicToken.class);
        verify(appointmentPublicTokenRepository).save(tokenCaptor.capture());
        AppointmentPublicToken persistedToken = tokenCaptor.getValue();

        assertThat(response.getCancelToken()).isNotBlank();
        assertThat(persistedToken.getTokenHash()).isNotEqualTo(response.getCancelToken());
        assertThat(persistedToken.getTokenHash()).isEqualTo(sha256Hex(response.getCancelToken()));
        assertThat(persistedToken.getType()).isEqualTo(AppointmentPublicTokenType.CANCEL);
        assertThat(persistedToken.getExpiresAt()).isEqualTo(startsAt);
        assertThat(persistedToken.getAppointmentId()).isEqualTo(response.getAppointmentId());
    }

    private void givenSuccessfulSpecificStaffAvailability(Long serviceOfferingId, Long staffMemberId,
            LocalDate requestedDate, LocalDateTime startsAt, LocalDateTime endsAt) {
        BookingSettings settings = enabledSettings();
        settings.setMinNoticeHours(0);
        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(staffServiceOfferingRepository.findAllByStaffMemberId(staffMemberId))
                .willReturn(List.of(relation(staffMemberId, serviceOfferingId)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, requestedDate, requestedDate, serviceOfferingId, staffMemberId, null))
                .willReturn(List.of(slot(startsAt, endsAt)));
    }

    private void givenSuccessfulCreateBase(Long serviceOfferingId, BookingSettings settings, LocalDateTime startsAt) {
        given(businessRepository.findBySlug(BUSINESS_SLUG)).willReturn(Optional.of(activeBusiness()));
        given(bookingSettingsRepository.findByBusinessId(BUSINESS_ID)).willReturn(Optional.of(settings));
        given(servOfferingRepository.findByIdAndBusinessId(serviceOfferingId, BUSINESS_ID))
                .willReturn(Optional.of(service(serviceOfferingId, "Haircut", ServiceOfferingStatus.ACTIVE)));
    }

    private void givenSuccessfulSpecificStaffCreate(Long serviceOfferingId, Long staffMemberId,
            BookingSettings settings, LocalDateTime startsAt) {
        LocalDate appointmentDate = startsAt.toLocalDate();

        givenSuccessfulCreateBase(serviceOfferingId, settings, startsAt);
        given(staffMemberRepository.findByIdAndBusinessId(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(staffServiceOfferingRepository.findAllByServiceOfferingId(serviceOfferingId))
                .willReturn(List.of(relation(staffMemberId, serviceOfferingId)));
        given(availabilityService.getAvailableSlotsForBusiness(
                BUSINESS_ID, appointmentDate, appointmentDate, serviceOfferingId, staffMemberId, null))
                .willReturn(List.of(slot(startsAt, startsAt.plusMinutes(30))));
        given(staffMemberRepository.findByIdAndBusinessIdForUpdate(staffMemberId, BUSINESS_ID))
                .willReturn(Optional.of(staff(staffMemberId, "John Doe", StaffMemberStatus.ACTIVE)));
        given(customerRepository.findByBusinessIdAndEmailIgnoreCase(BUSINESS_ID, "candela@email.com"))
                .willReturn(Optional.of(customer(500L)));
        given(appointmentRepository.save(any(Appointment.class))).willAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(900L);
            return appointment;
        });
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

    private Customer customer(Long id) {
        return Customer.builder()
                .id(id)
                .businessId(BUSINESS_ID)
                .name("Candela")
                .email("candela@email.com")
                .phoneNumber("1123456789")
                .status(CustomerStatus.ACTIVE)
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

    private AvailabilitySlotResponseDto slot(LocalDateTime startsAt, LocalDateTime endsAt) {
        return AvailabilitySlotResponseDto.builder()
                .startsAt(startsAt)
                .endsAt(endsAt)
                .available(true)
                .build();
    }

    private PublicAppointmentRequestDto appointmentRequest(Long serviceOfferingId, String staffMemberId,
            LocalDateTime startsAt) {
        PublicAppointmentCustomerRequestDto customer = new PublicAppointmentCustomerRequestDto();
        customer.setName("Candela");
        customer.setEmail("candela@email.com");
        customer.setPhoneNumber("1123456789");

        PublicAppointmentRequestDto request = new PublicAppointmentRequestDto();
        request.setServiceOfferingId(serviceOfferingId);
        request.setStaffMemberId(staffMemberId);
        request.setStartsAt(startsAt);
        request.setCustomer(customer);
        request.setCustomerNotes("Comentario opcional");
        return request;
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
