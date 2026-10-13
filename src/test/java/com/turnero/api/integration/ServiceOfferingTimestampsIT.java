package com.turnero.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnero.api.config.SessionProperties;
import com.turnero.api.model.Business;
import com.turnero.api.model.ServiceOffering;
import com.turnero.api.model.enums.BusinessOnboardingStatus;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.ServiceOfferingStatus;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.ServOfferingRepository;
import com.turnero.api.repository.UserRepository;
import com.turnero.api.service.SessionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:service_offering_timestamps;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@ActiveProfiles("test")
@Transactional
class ServiceOfferingTimestampsIT {
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ServOfferingRepository offerings;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionService sessions;
    @Autowired private SessionProperties sessionProperties;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;
    private Long businessId;

    @BeforeEach
    void setUp() {
        // Hibernate's test schema omits the NOT NULL constraints present in PostgreSQL.
        // Keep this database isolated so these constraints cannot change other fixtures.
        jdbc.execute("ALTER TABLE service_offerings ALTER COLUMN created_at SET NOT NULL");
        jdbc.execute("ALTER TABLE service_offerings ALTER COLUMN updated_at SET NOT NULL");
        LocalDateTime now = LocalDateTime.now();
        businessId = businessRepository.saveAndFlush(Business.builder()
                .name("Barber Studio").slug("timestamps-test").timezone("America/Argentina/Buenos_Aires")
                .status(BusinessStatus.ACTIVE).onboardingStatus(BusinessOnboardingStatus.PENDING_SETUP)
                .createdAt(now).updatedAt(now).build()).getId();
        var auth = new AdminAuthTestHelper(userRepository, sessions, sessionProperties);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").cookie(auth.ownerSessionCookie(businessId))).build();
    }

    @Test
    void createPersistsBothTimestampsWithoutReceivingThemFromTheClient() throws Exception {
        var response = mvc.perform(post("/api/v1/service-offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Depilación","category":"Depilación","duration_minutes":60,"price_cents":4000000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Depilación"))
                .andReturn().getResponse();
        Long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        offerings.flush();
        entityManager.clear();
        ServiceOffering persisted = offerings.findById(id).orElseThrow();
        assertThat(persisted.getBusinessId()).isEqualTo(businessId);
        assertThat(persisted.getStatus()).isEqualTo(ServiceOfferingStatus.ACTIVE);
        assertThat(persisted.getPriceCents()).isEqualTo(4000000);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCreatedAt());
    }

    @Test
    void editAndDeactivateRefreshUpdatedAtAndPreserveCreatedAt() throws Exception {
        LocalDateTime original = LocalDateTime.of(2026, 1, 1, 0, 0);
        Long id = offerings.saveAndFlush(ServiceOffering.builder()
                .businessId(businessId).name("Corte").durationMinutes(30).priceCents(600000)
                .status(ServiceOfferingStatus.ACTIVE).createdAt(original).updatedAt(original).build()).getId();
        entityManager.clear();
        mvc.perform(patch("/api/v1/service-offerings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Corte nuevo\"}"))
                .andExpect(status().isOk());
        offerings.flush();
        entityManager.clear();
        ServiceOffering edited = offerings.findById(id).orElseThrow();
        assertThat(edited.getCreatedAt()).isEqualTo(original);
        assertThat(edited.getUpdatedAt()).isAfter(original);
        LocalDateTime editedAt = edited.getUpdatedAt();

        mvc.perform(delete("/api/v1/service-offerings/{id}", id)).andExpect(status().isNoContent());
        offerings.flush();
        entityManager.clear();
        ServiceOffering deactivated = offerings.findById(id).orElseThrow();
        assertThat(deactivated.getStatus()).isEqualTo(ServiceOfferingStatus.INACTIVE);
        assertThat(deactivated.getCreatedAt()).isEqualTo(original);
        assertThat(deactivated.getUpdatedAt()).isAfterOrEqualTo(editedAt);
    }
}
