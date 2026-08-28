package com.turnero.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.model.Business;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.SessionService;
import com.turnero.api.config.SessionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

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
class BusinessControllerIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionService sessionService;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        businessRepository.deleteAll();
        businessRepository.flush();
        jdbcTemplate.execute("ALTER TABLE businesses ALTER COLUMN id RESTART WITH 1");
        businessRepository.saveAndFlush(Business.builder().name("Barber Studio").slug("barber-studio")
                .timezone("America/Argentina/Buenos_Aires").status(BusinessStatus.ACTIVE)
                .onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0)).updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .defaultRequest(get("/").cookie(adminAuth().ownerSessionCookie(1L)))
                .build();
    }

    private AdminAuthTestHelper adminAuth() {
        return new AdminAuthTestHelper(userRepository, sessionService, sessionProperties);
    }

    @Test
    void getBusiness_returnsBusinessFromCurrentContext() throws Exception {
        mockMvc.perform(get("/api/v1/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.slug").value("barber-studio"));
    }

    @Test
    void patchBusiness_persistsAllowedFieldsAndKeepsProtectedFields() throws Exception {
        mockMvc.perform(patch("/api/v1/business").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Barber Studio Palermo", "timezone", "UTC",
                                "slug", "other-slug", "status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Barber Studio Palermo"));

        Business updated = businessRepository.findById(1L).orElseThrow();
        assertThat(updated.getTimezone()).isEqualTo("UTC");
        assertThat(updated.getSlug()).isEqualTo("barber-studio");
        assertThat(updated.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(updated.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void patchBusiness_whenTimezoneIsInvalid_returnsBadRequestWithoutPersistingIt() throws Exception {
        mockMvc.perform(patch("/api/v1/business").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("timezone", "not-a-timezone"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(businessRepository.findById(1L).orElseThrow().getTimezone())
                .isEqualTo("America/Argentina/Buenos_Aires");
    }
}
