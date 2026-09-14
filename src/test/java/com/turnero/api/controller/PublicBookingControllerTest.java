package com.turnero.api.controller;

import com.turnero.api.auth.AdminAuthInterceptor;
import com.turnero.api.dto.PublicAvailabilitySlotResponseDto;
import com.turnero.api.dto.PublicBookingProfileResponseDto;
import com.turnero.api.dto.PublicBookingSettingsResponseDto;
import com.turnero.api.dto.PublicBusinessResponseDto;
import com.turnero.api.dto.PublicServiceOfferingListResponseDto;
import com.turnero.api.dto.PublicServiceOfferingResponseDto;
import com.turnero.api.dto.PublicStaffMemberResponseDto;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.service.PublicBookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicBookingController.class)
class PublicBookingControllerTest {

    private static final String BUSINESS_SLUG = "barber-studio";
    private static final String PROFILE_URL = "/api/v1/public/businesses/{businessSlug}/booking-profile";
    private static final String SERVICES_URL = "/api/v1/public/businesses/{businessSlug}/services";
    private static final String AVAILABILITY_URL = "/api/v1/public/businesses/{businessSlug}/availability";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PublicBookingService publicBookingService;
    @MockitoBean private AdminAuthInterceptor adminAuthInterceptor;

    @Test
    void getBookingProfile_returnsPublicProfileWithSnakeCaseContract() throws Exception {
        given(publicBookingService.getPublicBookingProfile(BUSINESS_SLUG)).willReturn(profileResponse());

        mockMvc.perform(get(PROFILE_URL, BUSINESS_SLUG))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.business.name").value("Barber Studio"))
                .andExpect(jsonPath("$.business.slug").value(BUSINESS_SLUG))
                .andExpect(jsonPath("$.business.industry").value("Barber"))
                .andExpect(jsonPath("$.business.timezone").value("America/Argentina/Buenos_Aires"))
                .andExpect(jsonPath("$.booking_settings.public_booking_enabled").value(true))
                .andExpect(jsonPath("$.booking_settings.booking_window_days").value(7))
                .andExpect(jsonPath("$.booking_settings.min_notice_hours").value(3))
                .andExpect(jsonPath("$.booking_settings.slot_interval_minutes").value(30))
                .andExpect(jsonPath("$.booking_settings.manual_confirmation_enabled").value(true))
                .andExpect(jsonPath("$.business.id").doesNotExist())
                .andExpect(jsonPath("$.business.business_id").doesNotExist())
                .andExpect(jsonPath("$.business.status").doesNotExist())
                .andExpect(jsonPath("$.business.created_at").doesNotExist())
                .andExpect(jsonPath("$.business.updated_at").doesNotExist())
                .andExpect(jsonPath("$.booking_settings.requires_customer_login").doesNotExist())
                .andExpect(jsonPath("$.booking_settings.cancellation_notice_hours").doesNotExist())
                .andExpect(jsonPath("$.booking_settings.whatsapp_reminders_enabled").doesNotExist());

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getBookingProfile_whenSlugDoesNotExist_returnsNotFound() throws Exception {
        given(publicBookingService.getPublicBookingProfile("missing-business"))
                .willThrow(new ResourceNotFoundException("Business not found"));

        mockMvc.perform(get(PROFILE_URL, "missing-business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Business not found"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getBookingProfile_whenBookingIsDisabled_returnsForbidden() throws Exception {
        given(publicBookingService.getPublicBookingProfile(BUSINESS_SLUG))
                .willThrow(new ForbiddenException("Public booking is disabled"));

        mockMvc.perform(get(PROFILE_URL, BUSINESS_SLUG))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Public booking is disabled"));

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getServices_returnsPublicServicesWithSnakeCaseContract() throws Exception {
        given(publicBookingService.getPublicServices(BUSINESS_SLUG)).willReturn(servicesResponse());

        mockMvc.perform(get(SERVICES_URL, BUSINESS_SLUG))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].id").value(10))
                .andExpect(jsonPath("$.services[0].name").value("Haircut"))
                .andExpect(jsonPath("$.services[0].category").value("Hair Services"))
                .andExpect(jsonPath("$.services[0].duration_minutes").value(30))
                .andExpect(jsonPath("$.services[0].price_cents").value(25000))
                .andExpect(jsonPath("$.services[0].staff_members.length()").value(1))
                .andExpect(jsonPath("$.services[0].staff_members[0].id").value(100))
                .andExpect(jsonPath("$.services[0].staff_members[0].name").value("John Doe"))
                .andExpect(jsonPath("$.services[0].staff_members[0].role_label").value("Barber"))
                .andExpect(jsonPath("$.services[0].staff_members[0].specialty").value("Haircut"))
                .andExpect(jsonPath("$.services[0].staff_members[0].avatar_url").value("https://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.services[0].business_id").doesNotExist())
                .andExpect(jsonPath("$.services[0].status").doesNotExist())
                .andExpect(jsonPath("$.services[0].created_at").doesNotExist())
                .andExpect(jsonPath("$.services[0].updated_at").doesNotExist())
                .andExpect(jsonPath("$.services[0].staff_members[0].business_id").doesNotExist())
                .andExpect(jsonPath("$.services[0].staff_members[0].status").doesNotExist())
                .andExpect(jsonPath("$.services[0].staff_members[0].created_at").doesNotExist())
                .andExpect(jsonPath("$.services[0].staff_members[0].updated_at").doesNotExist());

        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getAvailability_whenStaffMemberIdIsNumeric_returnsPublicAvailabilityWithSnakeCaseContract() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 15);
        LocalDate to = LocalDate.of(2026, 9, 15);
        Long serviceOfferingId = 10L;
        String staffMemberId = "100";

        given(publicBookingService.getPublicAvailability(BUSINESS_SLUG, from, to, serviceOfferingId, staffMemberId))
                .willReturn(List.of(availabilitySlot(
                        LocalDateTime.of(2026, 9, 15, 10, 0),
                        LocalDateTime.of(2026, 9, 15, 10, 30),
                        List.of(100L))));

        mockMvc.perform(get(AVAILABILITY_URL, BUSINESS_SLUG)
                        .param("from", "2026-09-15")
                        .param("to", "2026-09-15")
                        .param("service_offering_id", "10")
                        .param("staff_member_id", "100"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].starts_at").value("2026-09-15T10:00:00"))
                .andExpect(jsonPath("$[0].ends_at").value("2026-09-15T10:30:00"))
                .andExpect(jsonPath("$[0].available_staff_member_ids.length()").value(1))
                .andExpect(jsonPath("$[0].available_staff_member_ids[0]").value(100))
                .andExpect(jsonPath("$[0].startsAt").doesNotExist())
                .andExpect(jsonPath("$[0].endsAt").doesNotExist())
                .andExpect(jsonPath("$[0].availableStaffMemberIds").doesNotExist());

        then(publicBookingService).should()
                .getPublicAvailability(BUSINESS_SLUG, from, to, serviceOfferingId, staffMemberId);
        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    @Test
    void getAvailability_whenStaffMemberIdIsAny_returnsGroupedPublicAvailability() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 15);
        LocalDate to = LocalDate.of(2026, 9, 15);
        Long serviceOfferingId = 10L;

        given(publicBookingService.getPublicAvailability(BUSINESS_SLUG, from, to, serviceOfferingId, "any"))
                .willReturn(List.of(availabilitySlot(
                        LocalDateTime.of(2026, 9, 15, 10, 0),
                        LocalDateTime.of(2026, 9, 15, 10, 30),
                        List.of(100L, 200L))));

        mockMvc.perform(get(AVAILABILITY_URL, BUSINESS_SLUG)
                        .param("from", "2026-09-15")
                        .param("to", "2026-09-15")
                        .param("service_offering_id", "10")
                        .param("staff_member_id", "any"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].starts_at").value("2026-09-15T10:00:00"))
                .andExpect(jsonPath("$[0].ends_at").value("2026-09-15T10:30:00"))
                .andExpect(jsonPath("$[0].available_staff_member_ids.length()").value(2))
                .andExpect(jsonPath("$[0].available_staff_member_ids[0]").value(100))
                .andExpect(jsonPath("$[0].available_staff_member_ids[1]").value(200));

        then(publicBookingService).should()
                .getPublicAvailability(BUSINESS_SLUG, from, to, serviceOfferingId, "any");
        then(adminAuthInterceptor).shouldHaveNoInteractions();
    }

    private PublicBookingProfileResponseDto profileResponse() {
        return PublicBookingProfileResponseDto.builder()
                .business(PublicBusinessResponseDto.builder()
                        .name("Barber Studio")
                        .slug(BUSINESS_SLUG)
                        .industry("Barber")
                        .timezone("America/Argentina/Buenos_Aires")
                        .build())
                .bookingSettings(PublicBookingSettingsResponseDto.builder()
                        .isPublicBookingEnabled(true)
                        .bookingWindowDays(7)
                        .minNoticeHours(3)
                        .slotIntervalMinutes(30)
                        .manualConfirmationEnabled(true)
                        .build())
                .build();
    }

    private PublicServiceOfferingListResponseDto servicesResponse() {
        return PublicServiceOfferingListResponseDto.builder()
                .services(List.of(PublicServiceOfferingResponseDto.builder()
                        .id(10L)
                        .name("Haircut")
                        .category("Hair Services")
                        .durationMinutes(30)
                        .priceCents(25000)
                        .staffMembers(List.of(PublicStaffMemberResponseDto.builder()
                                .id(100L)
                                .name("John Doe")
                                .roleLabel("Barber")
                                .specialty("Haircut")
                                .avatarUrl("https://example.com/avatar.jpg")
                                .build()))
                        .build()))
                .build();
    }

    private PublicAvailabilitySlotResponseDto availabilitySlot(LocalDateTime startsAt, LocalDateTime endsAt,
            List<Long> availableStaffMemberIds) {
        return PublicAvailabilitySlotResponseDto.builder()
                .startsAt(startsAt)
                .endsAt(endsAt)
                .availableStaffMemberIds(availableStaffMemberIds)
                .build();
    }
}
