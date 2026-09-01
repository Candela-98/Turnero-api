package com.turnero.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.model.BookingSettings;
import com.turnero.api.model.Business;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.repository.BookingSettingsRepository;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingSettingsControllerIT {
    private static final String BASE_URL = "/api/v1/booking-settings";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingSettingsRepository bookingSettingsRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionService sessionService;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        bookingSettingsRepository.deleteAll();
        businessRepository.deleteAll();
        businessRepository.flush();
        jdbcTemplate.execute("ALTER TABLE businesses ALTER COLUMN id RESTART WITH 1");
        jdbcTemplate.execute("ALTER TABLE booking_settings ALTER COLUMN id RESTART WITH 1");
        businessRepository.saveAndFlush(Business.builder()
                .name("Barber Studio")
                .slug("barber-studio")
                .timezone("America/Argentina/Buenos_Aires")
                .status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build());
        bookingSettingsRepository.saveAndFlush(settings());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").cookie(adminAuth().ownerSessionCookie(1L)))
                .build();
    }

    private AdminAuthTestHelper adminAuth() {
        return new AdminAuthTestHelper(userRepository, sessionService, sessionProperties);
    }

    @Test
    void getBookingSettings_returnsSettingsForCurrentBusiness() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public_booking_enabled").value(true))
                .andExpect(jsonPath("$.booking_window_days").value(7))
                .andExpect(jsonPath("$.slot_interval_minutes").value(30))
                .andExpect(jsonPath("$.requires_customer_login").value(false));
    }

    @Test
    void patchBookingSettings_updatesValidSettings() throws Exception {
        mockMvc.perform(patch(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "public_booking_enabled", false,
                                "booking_window_days", 14,
                                "min_notice_hours", 4,
                                "cancellation_notice_hours", 2,
                                "slot_interval_minutes", 15,
                                "manual_confirmation_enabled", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.public_booking_enabled").value(false))
                .andExpect(jsonPath("$.slot_interval_minutes").value(15));

        BookingSettings updated = bookingSettingsRepository.findByBusinessId(1L).orElseThrow();
        assertThat(updated.getBookingWindowDays()).isEqualTo(14);
        assertThat(updated.getMinNoticeHours()).isEqualTo(4);
        assertThat(updated.getCancellationNoticeHours()).isEqualTo(2);
        assertThat(updated.isManualConfirmationEnabled()).isTrue();
    }

    @Test
    void patchBookingSettings_whenCustomerLoginIsRequired_returnsValidationError() throws Exception {
        mockMvc.perform(patch(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requires_customer_login\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patchBookingSettings_whenSlotIntervalIsNotAllowed_returnsBadRequest() throws Exception {
        mockMvc.perform(patch(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slot_interval_minutes\":20}"))
                .andExpect(status().isBadRequest());
    }

    private BookingSettings settings() {
        return BookingSettings.builder()
                .businessId(1L)
                .publicBookingEnabled(true)
                .requiresCustomerLogin(false)
                .bookingWindowDays(7)
                .minNoticeHours(3)
                .cancellationNoticeHours(3)
                .slotIntervalMinutes(30)
                .manualConfirmationEnabled(false)
                .whatsappRemindersEnabled(false)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }
}
