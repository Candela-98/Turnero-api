package com.turnero.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.turnero.api.TurneroApiApplication;
import com.turnero.api.model.Appointment;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.repository.AppointmentReadRepository;
import com.turnero.api.repository.AppointmentRepository;
import com.turnero.api.repository.StaffServiceOfferingRepository;
import com.turnero.api.repository.UserSessionRepository;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import org.springframework.data.domain.PageRequest;
import com.turnero.api.service.AppointmentReadQuery;
import com.turnero.api.service.BusinessLocalTimeRange;
import com.turnero.api.scheduler.SessionCleanupScheduler;
import com.turnero.api.scheduler.PublicTokenCleanupScheduler;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in laboratory, not a latency assertion or a production benchmark. */
@EnabledIfEnvironmentVariable(named = "TURNERO_QUERY_PLAN_AUDIT", matches = "true")
@SpringBootTest(classes = {AppointmentReadRepository.class, MvpQueryPlanPostgresIT.Configuration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class MvpQueryPlanPostgresIT {
    private static final long SEED = 114;
    private static final LocalDateTime REFERENCE = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int SAMPLES = 5;
    private static final List<String> LOADED_TABLES = List.of("businesses", "customers", "staff_members",
            "service_offerings", "staff_service_offerings", "appointments");
    private static final String APPOINTMENT_INSERT = """
            INSERT INTO appointments(id,business_id,customer_id,service_offering_id,staff_member_id,
            starts_at,ends_at,duration_minutes,price_cents,status,source,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,30,1000,?,?,?,?)
            """;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ThreadLocal<List<Execution>> CAPTURE = new ThreadLocal<>();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero_query_plan_only").withUsername("turnero").withPassword("turnero")
            .withCommand("postgres", "-c", "autovacuum=off");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MvpQueryPlanPostgresIT::jdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AppointmentReadRepository reads;
    @Autowired AppointmentRepository appointments;
    @Autowired StaffServiceOfferingRepository associations;
    @Autowired UserSessionRepository sessions;
    @Autowired AppointmentPublicTokenRepository tokens;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Flyway flyway;
    @Autowired ApplicationContext context;

    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<Association> links = new ArrayList<>();
    private final List<Map<String, Object>> measurements = new ArrayList<>();
    private final Map<String, String> referenceFingerprints = new LinkedHashMap<>();
    private final Map<String, String> referenceExecutions = new LinkedHashMap<>();
    private final Map<String, List<Long>> referenceResults = new LinkedHashMap<>();
    private Path output;
    private String variant;

    private static final String Q11_UNION = """
            SELECT id FROM (
                SELECT id FROM user_sessions WHERE revoked_at IS NOT NULL AND revoked_at < ?
                UNION ALL
                SELECT id FROM user_sessions WHERE revoked_at IS NULL AND expires_at < ?
            ) AS eligible
            ORDER BY id ASC
            LIMIT ?
            """;

    private record Q11Case(String name, List<CleanupFixture> rows, LocalDateTime cutoff) { }
    private record Q11Proof(List<Execution> statements, List<Long> expectedIds, String fingerprint, int eligible) { }

    @Test
    void measureQ11UnionAll() throws Exception {
        long started = System.nanoTime();
        Path run = Files.createDirectories(Path.of("build", "reports", "query-plan",
                Instant.now().toString().replace(':', '-') + "-" + postgres.getContainerId().substring(0, 12) + "-q11-union"));
        output = run;
        variant = "q11-union-experiment";
        var metadata = manifest();
        metadata.put("protocolRevision", "q11-union-all");
        write("manifest.json", metadata);
        try {
            flyway.validate();
            List<String> originalIndexes = indexDefinitions();
            assertThat(originalIndexes).hasSize(45);
            assertThat(jdbc.queryForObject("SHOW autovacuum", String.class)).isEqualTo("off");
            resetDataset();
            jdbc.update("""
                    INSERT INTO businesses(id,name,slug,timezone,status,onboarding_status,created_at,updated_at)
                    VALUES (1,'Q11 audit','q11-audit',?,'ACTIVE','READY',?,?)
                    """, ZONE.toString(), REFERENCE, REFERENCE);
            jdbc.update("""
                    INSERT INTO users(id,business_id,name,email,auth_provider,auth_subject,role,created_at,updated_at)
                    VALUES (1,1,'Audit user','audit@example.invalid','GOOGLE','synthetic-audit','OWNER',?,?)
                    """, REFERENCE, REFERENCE);
            LocalDateTime cutoff = REFERENCE.minusDays(30);
            // Use one microsecond, the precision stored by PostgreSQL timestamps.
            LocalDateTime before = cutoff.minusNanos(1_000);
            LocalDateTime after = cutoff.plusNanos(1_000);
            var boundary = new Q11Case("cutoff-boundaries", List.of(
                    new CleanupFixture(1L, 1L, true, cutoff, null),
                    new CleanupFixture(2L, 1L, true, before, null),
                    new CleanupFixture(3L, 1L, true, after, null),
                    new CleanupFixture(4L, 1L, true, after, before),
                    new CleanupFixture(5L, 1L, true, before, cutoff),
                    new CleanupFixture(6L, 1L, true, before, after),
                    new CleanupFixture(7L, 1L, true, before, before)), cutoff);
            List<Q11Case> cases = new ArrayList<>();
            cases.add(boundary);
            for (CleanupScenario scenario : List.of(new CleanupScenario(5_000, 50),
                    new CleanupScenario(5_000, 1_000), new CleanupScenario(20_000, 50))) {
                var rows = cleanupFixtures(CleanupTable.SESSIONS, scenario, cutoff, List.of(), List.of());
                assertThat(rows.stream().filter(row -> row.eligibleAt(cutoff)).count()).isEqualTo(scenario.eligible());
                cases.add(new Q11Case(scenario.name(), rows, cutoff));
            }
            cases.add(new Q11Case("empty-n5000", cases.get(1).rows(), cutoff.minusDays(3)));

            // Preflight every case before recording a single EXPLAIN ANALYZE sample.
            Map<String, Q11Proof> proofs = new LinkedHashMap<>();
            for (Q11Case scenario : cases) {
                rebuildCleanup(CleanupTable.SESSIONS, scenario.rows(), cutoff);
                Q11Proof proof = proveQ11Equivalence(scenario);
                if (scenario == boundary) assertThat(proof.expectedIds()).containsExactly(2L, 4L, 7L);
                if (scenario.name().equals("empty-n5000")) assertThat(proof.expectedIds()).isEmpty();
                proofs.put(scenario.name(), proof);
            }
            int ordinal = 0;
            for (Q11Case scenario : cases) {
                output = Files.createDirectories(run.resolve(scenario.name()));
                var preparation = rebuildCleanup(CleanupTable.SESSIONS, scenario.rows(), cutoff);
                Q11Proof proof = proveQ11Equivalence(scenario);
                assertThat(proof.fingerprint()).isEqualTo(proofs.get(scenario.name()).fingerprint());
                write("fixture.json", Map.of("rows", scenario.rows().size(), "eligible", proof.eligible(),
                        "ids", proof.expectedIds(), "cutoff", scenario.cutoff().toString(), "limit", 500,
                        "sha256", proof.fingerprint(), "preparation", preparation));
                exportDatabaseEvidence();
                for (int arm : ordinal++ % 2 == 0 ? List.of(0, 1) : List.of(1, 0)) {
                    measure(arm == 0 ? "Q11-or" : "Q11-union-all", scenario.name(),
                            proof.statements().get(arm), proof.eligible(), proof.expectedIds().size());
                }
                write("maintenance-after-measurement.json", maintenanceSnapshot());
                assertThat(indexDefinitions()).containsExactlyElementsOf(originalIndexes);
            }
            output = run;
            assertThat(measurements).hasSize(cases.size() * 2 * (SAMPLES + 1));
            write("indexes-after.json", indexDefinitions());
            resetDataset();
            write("status.json", Map.of("status", "complete", "recordedExecutions", measurements.size(),
                    "durationMs", elapsedMillis(started)));
        } catch (Exception | AssertionError failure) {
            output = run;
            write("status.json", Map.of("status", "failed", "exceptionType", failure.getClass().getName()));
            throw failure;
        } finally {
            output = run;
            write("measurements.json", measurements);
            writeSummary();
            System.out.println("Q11 UNION ALL evidence: " + run.toAbsolutePath());
        }
    }

    private Q11Proof proveQ11Equivalence(Q11Case scenario) throws Exception {
        List<Long> eligible = scenario.rows().stream().filter(row -> row.eligibleAt(scenario.cutoff()))
                .map(CleanupFixture::id).sorted().toList();
        List<Long> expected = eligible.stream().limit(500).toList();
        List<Execution> statements = capture(() -> {
            List<Long> baseline = sessions.findCleanupCandidateIds(scenario.cutoff(), PageRequest.of(0, 500));
            List<Long> union = jdbc.query(Q11_UNION, statement -> {
                var cutoff = java.sql.Timestamp.valueOf(scenario.cutoff());
                statement.setTimestamp(1, cutoff);
                statement.setTimestamp(2, cutoff);
                statement.setInt(3, 500);
            }, (row, rowNum) -> row.getLong(1));
            assertThat(baseline).as("Repository IDs and order: %s", scenario.name()).containsExactlyElementsOf(expected);
            assertThat(union).as("UNION ALL IDs and order: %s", scenario.name()).containsExactlyElementsOf(baseline);
        });
        assertThat(statements).hasSize(2);
        assertThat(statements.getLast().calls().stream().map(Call::evidence).toList())
                .containsExactlyElementsOf(statements.getFirst().calls().stream().map(Call::evidence).toList());
        return new Q11Proof(statements, expected, cleanupFingerprint(CleanupTable.SESSIONS), eligible.size());
    }

    private enum ExperimentalIndex {
        Q05("exp_appointments_staff_ends_at", "appointments", "staff_member_id, ends_at"),
        Q25("exp_staff_service_offerings_service", "staff_service_offerings", "service_offering_id");

        final String name, table, columns;
        ExperimentalIndex(String name, String table, String columns) {
            this.name = name;
            this.table = table;
            this.columns = columns;
        }
    }

    @Test
    void measureQ25ServiceCandidate() throws Exception {
        long started = System.nanoTime();
        Path run = Files.createDirectories(Path.of("build", "reports", "query-plan",
                Instant.now().toString().replace(':', '-') + "-" + postgres.getContainerId().substring(0, 12) + "-q25-service"));
        output = run;
        variant = "q25-experiment";
        var metadata = manifest();
        metadata.put("protocolRevision", "q25-service-candidate");
        write("manifest.json", metadata);
        List<String> originalIndexes = indexDefinitions();
        assertThat(originalIndexes).hasSize(45);
        assertThat(jdbc.queryForObject("SHOW autovacuum", String.class)).isEqualTo("off");
        flyway.validate();
        String allAssociations = "SELECT row_to_json(a)::text FROM staff_service_offerings a ORDER BY id";
        int readPlans = 0;
        int writePlans = 0;
        try {
            for (String dataset : List.of("base", "associations-expanded", "writes")) {
                referenceExecutions.clear();
                String fingerprint = null;
                boolean writing = dataset.equals("writes");
                List<Boolean> arms = dataset.equals("associations-expanded") ? List.of(true, false) : List.of(false, true);
                for (boolean candidate : arms) {
                    variant = candidate ? "candidate" : "baseline";
                    output = Files.createDirectories(run.resolve(dataset).resolve(variant));
                    measurements.clear();
                    changeExperimentalIndex(ExperimentalIndex.Q25, false);
                    resetDataset();
                    long loadStarted = System.nanoTime();
                    loadDataset();
                    if (!dataset.equals("base")) expandAssociations();
                    int count = dataset.equals("base") ? 1_960 : 7_220;
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_service_offerings", Long.class)).isEqualTo((long) count);
                    assertThat(links.stream().filter(link -> link.service() == 1).count()).isEqualTo(20);
                    assertThat(links.stream().filter(link -> link.service() == 2).count()).isEqualTo(1);
                    assertThat(links.stream().filter(link -> link.service() == 20).count()).isZero();
                    long loadMs = elapsedMillis(loadStarted);
                    prepareIndexEvidence(ExperimentalIndex.Q25, candidate, originalIndexes, loadMs);
                    String actual = fingerprint(allAssociations);
                    if (fingerprint == null) fingerprint = actual;
                    assertThat(actual).isEqualTo(fingerprint);
                    write("fixture.json", Map.of("sha256", actual, "associations", count));
                    if (writing) measureQ25Writes(allAssociations, actual, count);
                    else for (long service : List.of(1L, 2L, 20L)) measureAssociations(service);
                    finishIndexArm(ExperimentalIndex.Q25, originalIndexes, candidate, (writing ? 2 : 3) * (SAMPLES + 1));
                    if (writing) writePlans += measurements.size();
                    else readPlans += measurements.size();
                }
            }
            changeExperimentalIndex(ExperimentalIndex.Q25, false);
            assertThat(indexDefinitions()).containsExactlyElementsOf(originalIndexes);
            resetDataset();
            output = run;
            write("status.json", Map.of("status", "complete", "readPlans", readPlans, "writePlans", writePlans,
                    "durationMs", elapsedMillis(started)));
        } catch (Exception | AssertionError failure) {
            output = run;
            write("status.json", Map.of("status", "failed", "exceptionType", failure.getClass().getName()));
            throw failure;
        } finally {
            System.out.println("Q25 candidate evidence: " + run.toAbsolutePath());
        }
    }

    private void measureQ25Writes(String allAssociations, String original, int count) throws Exception {
        // Service 20 of each business remains unused in the expanded fixture.
        String insert = """
                INSERT INTO staff_service_offerings(id,staff_member_id,service_offering_id)
                SELECT id+100000,id,business_id*20 FROM staff_members WHERE id BETWEEN 1 AND 100
                """;
        String delete = "DELETE FROM staff_service_offerings WHERE id BETWEEN 1 AND 100";
        for (boolean inserting : List.of(true, false)) {
            String operation = inserting ? "insert-100" : "delete-100";
            Execution execution = new Execution(inserting ? insert : delete, List.of());
            for (int sample = 0; sample <= SAMPLES; sample++) {
                long maintenanceStarted = System.nanoTime();
                vacuumTables(List.of("staff_service_offerings"));
                long maintenanceMs = elapsedMillis(maintenanceStarted);
                JsonNode[] plan = new JsonNode[1];
                rollbackCleanup(status -> {
                    plan[0] = explain(execution, status);
                    String affected = inserting
                            ? "SELECT count(*) FROM staff_service_offerings a JOIN staff_members s ON s.id=a.staff_member_id WHERE a.id BETWEEN 100001 AND 100100 AND a.id=s.id+100000 AND a.service_offering_id=s.business_id*20"
                            : "SELECT 100-count(*) FROM staff_service_offerings WHERE id BETWEEN 1 AND 100";
                    assertThat(jdbc.queryForObject(affected, Long.class)).isEqualTo(100L);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_service_offerings", Long.class))
                            .isEqualTo(count + (inserting ? 100L : -100L));
                });
                assertThat(fingerprint(allAssociations)).isEqualTo(original);
                recordMeasurement("Q25-write", operation, sample, execution, 100, plan[0],
                        Map.of("verifiedAffectedRows", 100, "rollbackVerified", true, "maintenanceMs", maintenanceMs));
            }
        }
    }

    @Test
    void measureQ05EndsAtCandidate() throws Exception {
        long started = System.nanoTime();
        Path run = Files.createDirectories(Path.of("build", "reports", "query-plan",
                Instant.now().toString().replace(':', '-') + "-" + postgres.getContainerId().substring(0, 12) + "-q05-ends"));
        output = run;
        variant = "q05-experiment";
        write("manifest.json", manifest());
        List<String> originalIndexes = indexDefinitions();
        assertThat(originalIndexes).hasSize(45);
        assertThat(jdbc.queryForObject("SHOW autovacuum", String.class)).isEqualTo("off");
        flyway.validate();
        try {
            int datasetNumber = 0;
            for (String dataset : List.of("base", "history-plus-2000", "history-plus-8000", "future-control")) {
                referenceExecutions.clear();
                String fingerprint = null;
                List<Boolean> arms = datasetNumber++ % 2 == 0 ? List.of(false, true) : List.of(true, false);
                for (boolean candidate : arms) {
                    prepareQ05Arm(run, dataset, candidate, originalIndexes);
                    String actual = fingerprint("SELECT row_to_json(a)::text FROM appointments a ORDER BY id");
                    if (fingerprint == null) fingerprint = actual;
                    assertThat(actual).isEqualTo(fingerprint);
                    write("fixture.json", Map.of("sha256", actual, "appointments", fixtures.size(),
                            "futureControlRows", dataset.equals("future-control") ? 8_000 : 0));
                    measureAvailabilityScenarios(1L);
                    finishIndexArm(ExperimentalIndex.Q05, originalIndexes, candidate, 18);
                }
            }
            // Rebuild for the write arms after the entire read matrix is complete.
            for (boolean candidate : List.of(false, true)) {
                prepareQ05Arm(run, "writes", candidate, originalIndexes);
                measureQ05Writes();
                finishIndexArm(ExperimentalIndex.Q05, originalIndexes, candidate, 18);
            }
            changeExperimentalIndex(ExperimentalIndex.Q05, false);
            assertThat(indexDefinitions()).containsExactlyElementsOf(originalIndexes);
            resetDataset();
            output = run;
            write("status.json", Map.of("status", "complete", "readPlans", 144, "writePlans", 36,
                    "durationMs", elapsedMillis(started)));
        } catch (Exception | AssertionError failure) {
            output = run;
            write("status.json", Map.of("status", "failed", "exceptionType", failure.getClass().getName()));
            throw failure;
        } finally {
            System.out.println("Q05 candidate evidence: " + run.toAbsolutePath());
        }
    }

    private void changeExperimentalIndex(ExperimentalIndex index, boolean candidate) throws SQLException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            verifyContainer(connection);
            assertThat(connection.getAutoCommit()).isTrue();
            try (var statement = connection.createStatement()) {
                statement.execute("DROP INDEX IF EXISTS " + index.name);
                if (candidate) statement.execute("CREATE INDEX " + index.name + " ON " + index.table + " (" + index.columns + ")");
            }
        }
    }

    private void prepareQ05Arm(Path run, String dataset, boolean candidate, List<String> originalIndexes) throws Exception {
        variant = candidate ? "candidate" : "baseline";
        output = Files.createDirectories(run.resolve(dataset).resolve(variant));
        measurements.clear();
        changeExperimentalIndex(ExperimentalIndex.Q05, false);
        resetDataset();
        long started = System.nanoTime();
        loadDataset();
        if (dataset.equals("history-plus-2000")) addHistory(2_000);
        if (dataset.equals("history-plus-8000")) addHistory(8_000);
        if (dataset.equals("future-control")) addQ05Future();
        prepareIndexEvidence(ExperimentalIndex.Q05, candidate, originalIndexes, elapsedMillis(started));
    }

    private void prepareIndexEvidence(ExperimentalIndex index, boolean candidate, List<String> originalIndexes,
                                      long loadMs) throws Exception {
        long started = System.nanoTime();
        if (candidate) changeExperimentalIndex(index, true);
        long indexMs = elapsedMillis(started);
        started = System.nanoTime();
        vacuumDataset();
        long maintenanceMs = elapsedMillis(started);
        verifyExperimentalIndexes(index, originalIndexes, candidate);
        write("preparation.json", Map.of("loadMs", loadMs, "indexMs", indexMs, "maintenanceMs", maintenanceMs,
                "candidateBytes", candidate ? jdbc.queryForObject("SELECT pg_relation_size('" + index.name + "')", Long.class) : 0L));
        exportDatabaseEvidence();
    }

    private void verifyExperimentalIndexes(ExperimentalIndex index, List<String> originalIndexes, boolean candidate) {
        List<String> definitions = indexDefinitions();
        String prefix = "CREATE INDEX " + index.name + " ";
        assertThat(definitions.stream().filter(definition -> !definition.startsWith(prefix)).toList())
                .containsExactlyElementsOf(originalIndexes);
        List<String> experimental = definitions.stream().filter(definition -> definition.startsWith(prefix)).toList();
        assertThat(experimental).hasSize(candidate ? 1 : 0);
        if (candidate) assertThat(experimental.getFirst()).endsWith("USING btree (" + index.columns + ")");
    }

    private void finishIndexArm(ExperimentalIndex index, List<String> originalIndexes, boolean candidate, int expectedPlans) throws Exception {
        verifyExperimentalIndexes(index, originalIndexes, candidate);
        assertThat(measurements).hasSize(expectedPlans);
        write("maintenance-after-measurement.json", maintenanceSnapshot());
        write("measurements.json", measurements);
        writeSummary();
        write("status.json", Map.of("status", "complete", "recordedExecutions", expectedPlans));
    }

    private void addQ05Future() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<Object[]> rows = new ArrayList<>();
            LocalDateTime first = REFERENCE.withHour(0).plusDays(8);
            for (int offset = 0; offset < 8_000; offset++) {
                long id = 50_001L + offset;
                LocalDateTime start = first.plusHours(offset);
                Fixture fixture = new Fixture(id, 1, 1, start, start.plusMinutes(30), AppointmentStatus.CONFIRMED);
                fixtures.add(fixture);
                rows.add(new Object[]{id, 1L, 1L, 1L, 1L, start, fixture.end(), fixture.state().name(),
                        "ADMIN", REFERENCE, REFERENCE});
            }
            batch(APPOINTMENT_INSERT, rows);
        });
    }

    private void measureQ05Writes() throws Exception {
        String allAppointments = "SELECT row_to_json(a)::text FROM appointments a ORDER BY id";
        String original = fingerprint(allAppointments);
        write("fixture.json", Map.of("sha256", original, "appointments", fixtures.size()));
        Map<String, String> writes = new LinkedHashMap<>();
        writes.put("insert-100", """
                INSERT INTO appointments(id,business_id,customer_id,service_offering_id,staff_member_id,starts_at,ends_at,
                    duration_minutes,price_cents,status,source,created_at,updated_at)
                SELECT id+100000,business_id,customer_id,service_offering_id,staff_member_id,starts_at,ends_at,
                    duration_minutes,price_cents,status,source,created_at,updated_at
                FROM appointments WHERE id BETWEEN 1 AND 100
                """);
        writes.put("reschedule-100", """
                UPDATE appointments SET starts_at=starts_at+interval '1 day',ends_at=ends_at+interval '1 day'
                WHERE id BETWEEN 1 AND 100
                """);
        writes.put("delete-100", "DELETE FROM appointments WHERE id BETWEEN 1 AND 100");
        for (var operation : writes.entrySet()) for (int sample = 0; sample <= SAMPLES; sample++) {
            long maintenanceStarted = System.nanoTime();
            vacuumTables(List.of("appointments"));
            long maintenanceMs = elapsedMillis(maintenanceStarted);
            Execution execution = new Execution(operation.getValue(), List.of());
            JsonNode[] plan = new JsonNode[1];
            rollbackCleanup(status -> {
                plan[0] = explain(execution, status);
                String check = switch (operation.getKey()) {
                    case "insert-100" -> "SELECT count(*) FROM appointments WHERE id BETWEEN 100001 AND 100100";
                    case "delete-100" -> "SELECT 100-count(*) FROM appointments WHERE id BETWEEN 1 AND 100";
                    default -> """
                            SELECT count(*) FROM appointments WHERE id BETWEEN 1 AND 100 AND starts_at=? AND ends_at=?
                            """;
                };
                if (operation.getKey().equals("reschedule-100")) {
                    for (Fixture row : fixtures.subList(0, 100)) {
                        assertThat(jdbc.queryForObject(check + " AND id=?", Long.class, row.start().plusDays(1),
                                row.end().plusDays(1), row.id())).isEqualTo(1L);
                    }
                } else {
                    assertThat(jdbc.queryForObject(check, Long.class)).isEqualTo(100L);
                }
            });
            assertThat(fingerprint(allAppointments)).isEqualTo(original);
            recordMeasurement("Q05-write", operation.getKey(), sample, execution, 100, plan[0],
                    Map.of("verifiedAffectedRows", 100, "rollbackVerified", true, "maintenanceMs", maintenanceMs));
        }
    }

    @Test
    void measurePendingCriticalQueries() throws Exception {
        long started = System.nanoTime();
        variant = "critical-pending";
        Path run = Files.createDirectories(Path.of("build", "reports", "query-plan",
                Instant.now().toString().replace(':', '-') + "-" + postgres.getContainerId().substring(0, 12) + "-pending"));
        output = run;
        var metadata = manifest();
        metadata.put("protocolRevision", "phase3-cleanup-rollback");
        write("manifest.json", metadata);
        try {
            assertThat(context.getBeansOfType(TurneroApiApplication.class)).isEmpty();
            assertThat(context.getBeansOfType(SessionCleanupScheduler.class)).isEmpty();
            assertThat(context.getBeansOfType(PublicTokenCleanupScheduler.class)).isEmpty();
            assertThat(jdbc.queryForObject("SHOW autovacuum", String.class)).isEqualTo("off");
            flyway.validate();
            List<String> indexes = indexDefinitions();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND tablename <> 'flyway_schema_history'", Long.class))
                    .isEqualTo(43L);
            resetDataset();
            long loadStarted = System.nanoTime();
            loadDataset();
            long loadMs = elapsedMillis(loadStarted);
            long vacuumStarted = System.nanoTime();
            vacuumDataset();
            write("q04-base-preparation.json", Map.of("loadMs", loadMs, "vacuumMs", elapsedMillis(vacuumStarted)));
            measureConflictLookup(400L, 20L, "low-history");
            measureConflictLookup(1L, 1L, "base-history");
            loadStarted = System.nanoTime();
            addHistory(8_000);
            loadMs = elapsedMillis(loadStarted);
            vacuumStarted = System.nanoTime();
            vacuumDataset();
            write("q04-grown-preparation.json", Map.of("loadMs", loadMs, "vacuumMs", elapsedMillis(vacuumStarted)));
            measureConflictLookup(1L, 1L, "history-plus-8000");

            jdbc.update("""
                    INSERT INTO users(id,business_id,name,email,auth_provider,auth_subject,role,created_at,updated_at)
                    VALUES (1,1,'Audit user','audit@example.invalid','GOOGLE','synthetic-audit','OWNER',?,?)
                    """, REFERENCE, REFERENCE);
            // The first 50,000 appointments are the original fixture. Extra Q04 history is excluded.
            var businessAppointments = jdbc.queryForList("SELECT id FROM appointments WHERE business_id=1 AND id<=50000 ORDER BY id", Long.class);
            var otherAppointments = jdbc.queryForList("SELECT id FROM appointments WHERE business_id<>1 AND id<=50000 ORDER BY id", Long.class);
            for (CleanupTable table : CleanupTable.values()) {
                for (CleanupScenario scenario : List.of(new CleanupScenario(5_000, 50),
                        new CleanupScenario(5_000, 1_000), new CleanupScenario(20_000, 50))) {
                    measureCleanup(table, scenario, businessAppointments, otherAppointments);
                }
            }
            assertThat(indexDefinitions()).containsExactlyElementsOf(indexes);
            assertThat(measurements).hasSize(15 * (SAMPLES + 1));
            exportDatabaseEvidence();
            write("indexes-after.json", indexDefinitions());
            resetDataset();
            write("status.json", Map.of("status", "complete", "recordedExecutions", measurements.size(),
                    "durationMs", elapsedMillis(started)));
        } catch (Exception | AssertionError failure) {
            write("status.json", Map.of("status", "failed", "exceptionType", failure.getClass().getName()));
            throw failure;
        } finally {
            write("measurements.json", measurements);
            writeSummary();
            System.out.println("Pending query plan evidence: " + run.toAbsolutePath());
        }
    }

    private void measureConflictLookup(long staff, long business, String scenario) throws Exception {
        List<Long> expected = fixtures.stream().filter(f -> f.staff() == staff && f.business() == business)
                .map(Fixture::id).toList();
        long started = System.nanoTime();
        var captured = capture(() -> assertThat(appointments.findByStaffMemberIdAndBusinessId(staff, business)
                .stream().map(Appointment::getId).toList()).containsExactlyInAnyOrderElementsOf(expected));
        assertThat(captured).hasSize(1);
        write("q04-" + scenario + "-capture.json", Map.of("staff", staff, "business", business,
                "rows", expected.size(), "captureMs", elapsedMillis(started)));
        measure("Q04", scenario, captured.getFirst(), expected.size(), expected.size());
    }

    private enum CleanupTable {
        SESSIONS("user_sessions", "Q11", "Q12", "session_token_hash"),
        TOKENS("appointment_public_tokens", "Q16", "Q17", "token_hash");

        final String table, selectQuery, deleteQuery, secretColumn;
        CleanupTable(String table, String selectQuery, String deleteQuery, String secretColumn) {
            this.table = table;
            this.selectQuery = selectQuery;
            this.deleteQuery = deleteQuery;
            this.secretColumn = secretColumn;
        }
    }

    private record CleanupScenario(int size, int eligible) {
        String name() { return "n" + size + "-eligible" + eligible; }
    }
    private record CleanupFixture(long id, long parent, boolean owned, LocalDateTime expires, LocalDateTime marker) {
        boolean eligibleAt(LocalDateTime cutoff) { return owned && (marker == null ? expires : marker).isBefore(cutoff); }
    }

    private List<CleanupFixture> cleanupFixtures(CleanupTable table, CleanupScenario scenario, LocalDateTime cutoff,
                                                 List<Long> businessAppointments, List<Long> otherAppointments) {
        List<CleanupFixture> rows = new ArrayList<>(scenario.size());
        int own = 0, other = 0;
        for (int id = 1; id <= scenario.size(); id++) {
            boolean owned = table == CleanupTable.SESSIONS || (id <= 5_000 && id % 2 == 1);
            long parent = table == CleanupTable.SESSIONS ? 1L : owned ? businessAppointments.get(own++) : otherAppointments.get(other++);
            LocalDateTime expires = cutoff.plusSeconds(1), marker = null;
            if (owned && id % 2 == 1 && id < 2 * scenario.eligible()) {
                expires = cutoff.minusSeconds(1);
                if (id % 4 == 3) marker = cutoff.minusDays(1);
            } else if (!owned && id == 2) {
                expires = cutoff.minusDays(1);
            } else {
                switch (id % 6) {
                    case 0, 3 -> expires = cutoff;
                    case 1 -> { expires = cutoff.minusDays(1); marker = cutoff.plusSeconds(1); }
                    case 2, 5 -> { expires = cutoff.minusDays(1); marker = cutoff; }
                    default -> { }
                }
            }
            rows.add(new CleanupFixture(id, parent, owned, expires, marker));
        }
        return rows;
    }

    private List<Long> selectCleanup(CleanupTable table, LocalDateTime cutoff, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return table == CleanupTable.SESSIONS ? sessions.findCleanupCandidateIds(cutoff, page)
                : tokens.findCleanupCandidateIds(1L, cutoff, page);
    }

    private int deleteCleanup(CleanupTable table, List<Long> ids, LocalDateTime cutoff) {
        return table == CleanupTable.SESSIONS ? sessions.deleteCleanupCandidatesByIds(ids, cutoff)
                : tokens.deleteCleanupCandidatesByIds(1L, ids, cutoff);
    }

    private void measureCleanup(CleanupTable table, CleanupScenario scenario, List<Long> businessAppointments,
                                List<Long> otherAppointments) throws Exception {
        ZoneId zone = table == CleanupTable.TOKENS
                ? ZoneId.of(jdbc.queryForObject("SELECT timezone FROM businesses WHERE id=1", String.class)) : ZONE;
        LocalDateTime cutoff = REFERENCE.atZone(ZONE).toInstant().atZone(zone).toLocalDateTime().minusDays(30);
        List<CleanupFixture> rows = cleanupFixtures(table, scenario, cutoff, businessAppointments, otherAppointments);
        List<Long> eligible = rows.stream().filter(row -> row.eligibleAt(cutoff)).map(CleanupFixture::id).toList();
        assertThat(eligible).hasSize(scenario.eligible());
        List<Long> lot = eligible.stream().limit(500).toList();
        List<Long> allIds = rows.stream().map(CleanupFixture::id).toList();
        var deletedIds = new java.util.HashSet<>(lot);
        List<Long> survivors = allIds.stream().filter(id -> !deletedIds.contains(id)).toList();
        String name = scenario.name(), prefix = table.selectQuery + "-" + name;
        Map<String, Long> preparation = rebuildCleanup(table, rows, cutoff);
        String original = cleanupFingerprint(table);
        long captureStarted = System.nanoTime();
        List<Execution> captured = new ArrayList<>();
        rollbackCleanup(status -> {
            captured.addAll(capture(() -> {
                assertThat(selectCleanup(table, cutoff, 500)).containsExactlyElementsOf(lot);
                assertThat(deleteCleanup(table, lot, cutoff)).isEqualTo(lot.size());
            }));
            assertCleanupIds(table, survivors);
        });
        long captureMs = elapsedMillis(captureStarted);
        assertThat(captured).hasSize(2);
        assertCleanupRestored(table, allIds, original);
        assertThat(selectCleanup(table, cutoff.minusDays(3), 500)).isEmpty();
        if (table == CleanupTable.TOKENS) {
            assertThat(selectCleanup(table, cutoff, 17)).containsExactlyElementsOf(lot.subList(0, 17));
        }
        // Include exact-cutoff and recent-marker rows, plus an eligible token of another business.
        List<Long> controls = new ArrayList<>(List.of(4_995L, 4_997L, 4_999L));
        if (table == CleanupTable.TOKENS) controls.add(2L);
        assertThat(rows.stream().filter(row -> controls.contains(row.id())).noneMatch(row -> row.eligibleAt(cutoff))).isTrue();
        rollbackCleanup(status -> {
            List<Long> mixed = new ArrayList<>(lot);
            mixed.addAll(controls);
            assertThat(deleteCleanup(table, mixed, cutoff)).isEqualTo(lot.size());
            assertCleanupIds(table, survivors);
        });
        assertCleanupRestored(table, allIds, original);
        var initialSample = rebuildCleanup(table, rows, cutoff);
        assertCleanupRestored(table, allIds, original);
        write(prefix + "-fixture.json", Map.ofEntries(Map.entry("rows", scenario.size()),
                Map.entry("eligible", eligible.size()), Map.entry("eligibleWithoutMarker", rows.stream()
                        .filter(row -> row.eligibleAt(cutoff) && row.marker() == null).count()),
                Map.entry("eligibleWithMarker", rows.stream().filter(row -> row.eligibleAt(cutoff) && row.marker() != null).count()),
                Map.entry("cutoff", cutoff.toString()), Map.entry("zone", zone.toString()),
                Map.entry("lot", lot), Map.entry("budget", 500), Map.entry("captureMs", captureMs),
                Map.entry("capturePreparation", preparation), Map.entry("revalidationControls", controls),
                Map.entry("rollbackVerified", true)));
        write(prefix + "-maintenance.json", maintenanceSnapshot());
        measure(table.selectQuery, name, captured.getFirst(), eligible.size(), lot.size());
        Execution delete = captured.getLast();
        assertThat(delete.sql().stripLeading().toLowerCase(java.util.Locale.ROOT)).startsWith("delete from " + table.table + " ");
        for (int sample = 0; sample <= SAMPLES; sample++) {
            var samplePreparation = sample == 0 ? initialSample : rebuildCleanup(table, rows, cutoff);
            assertCleanupRestored(table, allIds, original);
            long sampleStarted = System.nanoTime();
            JsonNode[] result = new JsonNode[1];
            rollbackCleanup(status -> {
                assertThat(selectCleanup(table, cutoff, 500)).containsExactlyElementsOf(lot);
                result[0] = explain(delete, status);
                assertCleanupIds(table, survivors);
            });
            long transactionMs = elapsedMillis(sampleStarted);
            assertCleanupRestored(table, allIds, original);
            var verification = Map.of("expectedDeletedRows", lot.size(), "verifiedDeletedRows", lot.size(),
                    "rollbackVerified", true, "preparation", samplePreparation, "transactionMs", transactionMs);
            recordMeasurement(table.deleteQuery, name, sample, delete, lot.size(), result[0], verification);
        }
    }

    private Map<String, Long> rebuildCleanup(CleanupTable table, List<CleanupFixture> rows, LocalDateTime cutoff) throws SQLException {
        long started = System.nanoTime();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            verifyContainer(connection);
            assertThat(connection.getAutoCommit()).isTrue();
            try (var statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE " + table.table + " RESTART IDENTITY");
            }
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LocalDateTime created = cutoff.minusDays(7);
            if (table == CleanupTable.SESSIONS) {
                batch("""
                        INSERT INTO user_sessions(id,user_id,session_token_hash,created_at,expires_at,last_seen_at,revoked_at)
                        VALUES (?,?,?,?,?,?,?)
                        """, rows.stream().map(row -> new Object[]{row.id(), row.parent(), "synthetic-session-" + row.id(),
                        created, row.expires(), created, row.marker()}).toList());
            } else {
                batch("""
                        INSERT INTO appointment_public_tokens(id,appointment_id,token_hash,type,expires_at,used_at,created_at)
                        VALUES (?,?,?,'CANCEL',?,?,?)
                        """, rows.stream().map(row -> new Object[]{row.id(), row.parent(), "synthetic-public-" + row.id(),
                        row.expires(), row.marker(), created}).toList());
            }
        });
        long loadMs = elapsedMillis(started);
        started = System.nanoTime();
        vacuumTables(List.of(table.table));
        return Map.of("loadMs", loadMs, "vacuumMs", elapsedMillis(started));
    }

    private void rollbackCleanup(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.execute((Connection connection) -> { verifyContainer(connection); return null; });
            jdbc.execute("SET LOCAL statement_timeout = '120s'");
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            action.accept(status);
        });
    }

    private String cleanupFingerprint(CleanupTable table) throws Exception {
        return fingerprint("SELECT (to_jsonb(t)-'" + table.secretColumn + "')::text FROM " + table.table + " t ORDER BY id");
    }

    private void assertCleanupIds(CleanupTable table, List<Long> expected) {
        assertThat(jdbc.queryForList("SELECT id FROM " + table.table + " ORDER BY id", Long.class))
                .containsExactlyElementsOf(expected);
    }

    private void assertCleanupRestored(CleanupTable table, List<Long> allIds, String original) throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertCleanupIds(table, allIds);
        assertThat(cleanupFingerprint(table)).isEqualTo(original);
    }

    @Test
    void measureExistingIndexesOnDeterministicDataset() throws Exception {
        assertThat(context.getBeansOfType(TurneroApiApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(SessionCleanupScheduler.class)).isEmpty();
        assertThat(context.getBeansOfType(PublicTokenCleanupScheduler.class)).isEmpty();
        try (Connection connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            verifyContainer(connection);
        }
        long started = System.nanoTime();
        Path run = Files.createDirectories(Path.of("build", "reports", "query-plan",
                Instant.now().toString().replace(':', '-') + "-" + postgres.getContainerId().substring(0, 12)));
        output = run;
        write("manifest.json", manifest());
        List<Map<String, Object>> variants = new ArrayList<>();
        try {
            flyway.validate();
            assertThat(Arrays.stream(flyway.info().applied()).map(info -> info.getVersion().getVersion()).toList())
                    .containsExactly("1", "3", "4", "1002", "1003", "1004");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM businesses", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SHOW autovacuum", String.class)).isEqualTo("off");
            List<String> originalIndexes = indexDefinitions();
            for (String name : List.of("v2-vacuumed", "history-plus-2000", "history-plus-8000", "associations-expanded")) {
                variant = name;
                output = Files.createDirectories(run.resolve(name));
                variants.add(runVariant(originalIndexes));
            }
            output = run;
            write("status.json", Map.of("status", "complete", "variants", variants, "durationMs", elapsedMillis(started)));
        } catch (Exception | AssertionError failure) {
            JSON.writeValue(run.resolve("status.json").toFile(), Map.of("status", "failed", "completedVariants", variants,
                    "exceptionType", failure.getClass().getName(), "durationMs", elapsedMillis(started)));
            throw failure;
        } finally {
            System.out.println("Query plan evidence: " + run.toAbsolutePath());
        }
    }

    private Map<String, Object> runVariant(List<String> originalIndexes) throws Exception {
        long started = System.nanoTime();
        write("manifest.json", manifest());
        measurements.clear();
        try {
            resetDataset();
            loadDataset();
            int history = variant.equals("history-plus-2000") ? 2_000 : variant.equals("history-plus-8000") ? 8_000 : 0;
            if (history > 0) addHistory(history);
            if (variant.equals("associations-expanded")) expandAssociations();
            validateStateDistribution();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Long.class)).isEqualTo(50_000L + history);
            verifyEquivalentData();
            long loadMs = elapsedMillis(started);
            write("maintenance-before-vacuum.json", maintenanceSnapshot());
            long vacuumStarted = System.nanoTime();
            vacuumDataset();
            long vacuumMs = elapsedMillis(vacuumStarted);
            List<Map<String, Object>> visibleBefore = visibilitySnapshot();
            for (var table : visibleBefore) assertThat(((Number) table.get("relallvisible")).longValue())
                    .as("All loaded pages visible after VACUUM: %s", table.get("relname"))
                    .isEqualTo(((Number) table.get("relpages")).longValue());
            assertThat(indexDefinitions()).containsExactlyElementsOf(originalIndexes);
            write("maintenance-before-measurement.json", maintenanceSnapshot());
            exportDatabaseEvidence();
            long measuringStarted = System.nanoTime();
            if (variant.equals("v2-vacuumed")) {
                measureAgenda();
                measureHistoricalAgenda();
                for (long staff : List.of(1L, 20L, 400L)) measureAvailabilityScenarios(staff);
            } else if (history > 0) {
                measureAvailabilityScenarios(1L);
            }
            if (history == 0) for (long service : List.of(1L, 2L, 20L)) measureAssociations(service);
            long measurementMs = elapsedMillis(measuringStarted);
            int cases = variant.equals("v2-vacuumed") ? 54 : 3;
            assertThat(measurements).hasSize(cases * (SAMPLES + 1));
            assertThat(visibilitySnapshot()).containsExactlyElementsOf(visibleBefore);
            assertThat(indexDefinitions()).containsExactlyElementsOf(originalIndexes);
            write("maintenance-after-measurement.json", maintenanceSnapshot());
            write("indexes-after.json", indexDefinitions());
            Map<String, Object> status = Map.of("status", "complete", "variant", variant,
                    "recordedExecutions", measurements.size(), "durationMs", elapsedMillis(started),
                    "loadAndValidationMs", loadMs, "vacuumMs", vacuumMs, "measurementMs", measurementMs);
            write("status.json", status);
            return status;
        } catch (Exception | AssertionError failure) {
            write("status.json", Map.of("status", "failed", "variant", variant, "exceptionType", failure.getClass().getName()));
            throw failure;
        } finally {
            write("measurements.json", measurements);
            writeSummary();
        }
    }

    private static long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000; }

    private void resetDataset() throws SQLException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            verifyContainer(connection);
            assertThat(connection.getAutoCommit()).isTrue();
            try (var statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE businesses RESTART IDENTITY CASCADE");
            }
        }
        fixtures.clear();
        links.clear();
    }

    private List<String> indexDefinitions() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND (NOT i.indisvalid OR NOT i.indisready)", Long.class)).isZero();
        return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() ORDER BY tablename,indexname", String.class);
    }

    private List<Map<String, Object>> visibilitySnapshot() {
        return jdbc.queryForList("SELECT relname,relpages,relallvisible FROM pg_class WHERE relnamespace=current_schema()::regnamespace AND relname IN ('businesses','customers','staff_members','service_offerings','staff_service_offerings','appointments') ORDER BY relname");
    }

    private List<Map<String, Object>> maintenanceSnapshot() {
        jdbc.execute("SELECT pg_stat_clear_snapshot()");
        return jdbc.queryForList("""
                SELECT c.relname,c.relpages,c.relallvisible,c.reltuples,s.last_vacuum,s.last_autovacuum,
                  s.last_analyze,s.last_autoanalyze,s.vacuum_count,s.autovacuum_count,s.analyze_count,s.autoanalyze_count,
                  s.n_live_tup,s.n_dead_tup
                FROM pg_class c JOIN pg_stat_user_tables s ON s.relid=c.oid
                WHERE c.relnamespace=current_schema()::regnamespace ORDER BY c.relname
                """);
    }

    private void vacuumDataset() throws SQLException {
        vacuumTables(LOADED_TABLES);
    }

    private void vacuumTables(List<String> tables) throws SQLException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            verifyContainer(connection);
            assertThat(connection.getAutoCommit()).isTrue();
            try (var statement = connection.createStatement()) {
                statement.execute("SET statement_timeout='120s'");
                statement.execute("SET lock_timeout='5s'");
                try {
                    for (String table : tables) statement.execute("VACUUM (ANALYZE) " + table);
                    statement.execute("SELECT pg_stat_force_next_flush()");
                } finally {
                    statement.execute("RESET statement_timeout");
                    statement.execute("RESET lock_timeout");
                }
            }
        }
    }

    private Map<String, Object> manifest() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Process git = new ProcessBuilder("git", "rev-parse", "HEAD").start();
        result.put("commit", new String(git.getInputStream().readAllBytes()).trim());
        assertThat(git.waitFor()).isZero();
        result.put("synthetic", true);
        result.put("seed", SEED);
        result.put("datasetRevision", "v2-independent-state-cycles");
        result.put("protocolRevision", "phase2-controlled-vacuum");
        result.put("variant", variant == null ? "all-four" : variant);
        result.put("autovacuum", jdbc.queryForObject("SHOW autovacuum", String.class));
        result.put("stateDistribution", "Seeded shuffled five-state cycles per professional; independent RNG; boundary appointment remains CONFIRMED");
        result.put("referenceLocal", REFERENCE.toString());
        result.put("businessZone", ZONE.toString());
        result.put("image", postgres.getDockerImageName());
        result.put("imageId", postgres.getContainerInfo().getImageId());
        result.put("postgres", jdbc.queryForObject("SELECT version()", String.class));
        result.put("java", System.getProperty("java.version"));
        result.put("hibernate", org.hibernate.Version.getVersionString());
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            result.put("jdbcDriver", connection.getMetaData().getDriverVersion());
        }
        result.put("prepareThreshold", 0);
        result.put("preparationNote", "Unnamed JDBC plans; production server-prepared/generic plans not evaluated");
        result.put("captureExecutionBeforeWarmup", true);
        result.put("warmups", 1);
        result.put("samples", SAMPLES);
        result.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        result.put("containerHostConfig", Map.of("memoryLimitBytes", Objects.toString(postgres.getContainerInfo().getHostConfig().getMemory(), "unset"),
                "nanoCpus", Objects.toString(postgres.getContainerInfo().getHostConfig().getNanoCPUs(), "unset")));
        result.put("limits", List.of("Synthetic moderate volume; no production distribution assumed",
                "Warm cache; no cold-cache guarantee; EXPLAIN instrumentation overhead",
                "Bulk-loaded fresh heap; not an aged production database", "No candidate indexes; dashboard pending TURN-107"));
        return result;
    }

    private void loadDataset() {
        // Each variant is regenerated from an empty, verified, dedicated container database.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM businesses", Long.class)).isZero();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            List<Object[]> businesses = new ArrayList<>(), customers = new ArrayList<>(), staff = new ArrayList<>(), services = new ArrayList<>();
            for (long b = 1; b <= 20; b++) {
                businesses.add(new Object[]{b, "Synthetic business " + b, "query-plan-" + b, ZONE.toString(), REFERENCE, REFERENCE});
                for (int j = 0; j < 200; j++) customers.add(new Object[]{(b - 1) * 200 + j + 1, b, "Synthetic customer " + j, REFERENCE, REFERENCE});
                for (int j = 0; j < 20; j++) {
                    long id = (b - 1) * 20 + j + 1;
                    staff.add(new Object[]{id, b, "Synthetic staff " + j, REFERENCE, REFERENCE});
                    services.add(new Object[]{id, b, "Synthetic service " + j, REFERENCE, REFERENCE});
                    links.add(new Association(id, (b - 1) * 20 + 1));
                    if (j == 0) links.add(new Association(id, (b - 1) * 20 + 2));
                    for (int k = 0; k < 1 + j % 7; k++) links.add(new Association(id, (b - 1) * 20 + 3 + (j + k) % 17));
                }
            }
            batch("INSERT INTO businesses(id,name,slug,timezone,status,onboarding_status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE','READY',?,?)", businesses);
            batch("INSERT INTO customers(id,business_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)", customers);
            batch("INSERT INTO staff_members(id,business_id,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)", staff);
            batch("INSERT INTO service_offerings(id,business_id,name,status,duration_minutes,price_cents,created_at,updated_at) VALUES (?,?,?,'ACTIVE',30,1000,?,?)", services);
            batch("INSERT INTO staff_service_offerings(staff_member_id,service_offering_id) VALUES (?,?)",
                    links.stream().map(link -> new Object[]{link.staff(), link.service()}).toList());
            Random random = new Random(SEED);
            // A separate stream preserves businesses, dates, professionals and customers from v1.
            Random stateRandom = new Random(SEED ^ 0x5DEECE66DL);
            Map<Long, List<AppointmentStatus>> stateCycles = new LinkedHashMap<>();
            Map<Long, Integer> staffOrdinals = new LinkedHashMap<>();
            List<Object[]> rows = new ArrayList<>();
            for (long id = 1; id <= 50_000; id++) {
                long b = id % 5 < 2 ? 1 + random.nextInt(2) : 3 + random.nextInt(18);
                long member = (b - 1) * 20 + (id % 4 == 0 ? 1 : 2 + random.nextInt(19));
                LocalDateTime start = REFERENCE.toLocalDate().minusDays(90).plusDays(random.nextInt(98))
                        .atTime(8 + random.nextInt(10), random.nextInt(4) * 15);
                if (id == 50_000) { b = 1; member = 1; start = REFERENCE.withHour(0).minusMinutes(15); }
                int ordinal = staffOrdinals.merge(member, 1, Integer::sum) - 1;
                List<AppointmentStatus> cycle = stateCycles.computeIfAbsent(member,
                        ignored -> new ArrayList<>(List.of(AppointmentStatus.values())));
                if (ordinal % cycle.size() == 0) Collections.shuffle(cycle, stateRandom);
                AppointmentStatus state = id == 50_000 ? AppointmentStatus.CONFIRMED : cycle.get(ordinal % cycle.size());
                Fixture fixture = new Fixture(id, b, member, start, start.plusMinutes(30), state);
                fixtures.add(fixture);
                rows.add(new Object[]{id, b, (b - 1) * 200 + 1 + random.nextInt(200), (b - 1) * 20 + 1,
                        member, start, fixture.end(), state.name(), id % 2 == 0 ? "ADMIN" : "PUBLIC_BOOKING", REFERENCE, REFERENCE});
            }
            batch(APPOINTMENT_INSERT, rows);
            // Explicit IDs must not leave sequences behind their data, even in a disposable fixture.
            for (String table : List.of("businesses", "customers", "staff_members", "service_offerings", "appointments")) {
                jdbc.execute("SELECT setval(pg_get_serial_sequence('" + table + "','id'), (SELECT max(id) FROM " + table + "), true)");
            }
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Long.class)).isEqualTo(50_000);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM appointments a JOIN customers c ON c.id=a.customer_id
                JOIN staff_members p ON p.id=a.staff_member_id JOIN service_offerings s ON s.id=a.service_offering_id
                WHERE a.business_id<>c.business_id OR a.business_id<>p.business_id OR a.business_id<>s.business_id
                """, Long.class)).isZero();
        validateStateDistribution();
    }

    private void validateStateDistribution() {
        Map<Long, long[]> byBusiness = new LinkedHashMap<>(), byStaff = new LinkedHashMap<>();
        for (var row : jdbc.queryForList("SELECT business_id,staff_member_id,status,count(*) AS n FROM appointments GROUP BY business_id,staff_member_id,status")) {
            int state = AppointmentStatus.valueOf((String) row.get("status")).ordinal();
            long count = ((Number) row.get("n")).longValue();
            byBusiness.computeIfAbsent(((Number) row.get("business_id")).longValue(), ignored -> new long[5])[state] += count;
            byStaff.computeIfAbsent(((Number) row.get("staff_member_id")).longValue(), ignored -> new long[5])[state] += count;
        }
        assertThat(byBusiness).hasSize(20);
        assertThat(byStaff).hasSize(400);
        byStaff.forEach((staff, counts) -> {
            for (long count : counts) assertThat(count).as("Each state must exist for staff %s", staff).isPositive();
            // Complete cycles are balanced; one forced boundary appointment permits a difference of two.
            assertThat(Arrays.stream(counts).max().orElseThrow() - Arrays.stream(counts).min().orElseThrow())
                    .as("Balanced state cycles for staff %s", staff).isLessThanOrEqualTo(2);
        });
        byBusiness.forEach((business, counts) -> {
            long target = Arrays.stream(counts).sum() / counts.length;
            // At most one incomplete cycle per each of 20 staff, plus the boundary override.
            for (long count : counts) assertThat(count).as("State quota for business %s", business)
                    .isBetween(Math.max(1, target - 21), target + 21);
        });
        assertThat(Arrays.stream(byStaff.get(1L)).sum()).as("Q05 high versus medium load")
                .isGreaterThan(Arrays.stream(byStaff.get(20L)).sum());
        assertThat(Arrays.stream(byStaff.get(20L)).sum()).as("Q05 medium versus low load")
                .isGreaterThan(Arrays.stream(byStaff.get(400L)).sum());
        for (long staff : List.of(1L, 20L, 400L)) {
            List<Fixture> recent = fixtures.stream().filter(f -> f.staff() == staff
                    && f.start().isBefore(REFERENCE.withHour(0).plusDays(7)) && f.end().isAfter(REFERENCE.withHour(0))).toList();
            assertThat(recent).as("Q05 staff %s must have blocking and non-blocking appointments in seven days", staff)
                    .anyMatch(f -> f.state() == AppointmentStatus.PENDING || f.state() == AppointmentStatus.CONFIRMED)
                    .anyMatch(f -> f.state() != AppointmentStatus.PENDING && f.state() != AppointmentStatus.CONFIRMED);
        }
    }

    private void addHistory(int count) {
        assertThat(count % AppointmentStatus.values().length).isZero();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Random random = new Random(SEED ^ 0x48495354L);
            Random stateRandom = new Random(SEED ^ 0x4849535354L);
            List<AppointmentStatus> cycle = new ArrayList<>(List.of(AppointmentStatus.values()));
            List<Object[]> rows = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                if (index % cycle.size() == 0) Collections.shuffle(cycle, stateRandom);
                long id = 50_001L + index;
                LocalDateTime start = REFERENCE.toLocalDate().minusDays(91 + random.nextInt(275))
                        .atTime(8 + random.nextInt(10), random.nextInt(4) * 15);
                Fixture fixture = new Fixture(id, 1, 1, start, start.plusMinutes(30), cycle.get(index % cycle.size()));
                assertThat(fixture.end()).isBefore(REFERENCE.toLocalDate().minusDays(90).atStartOfDay());
                fixtures.add(fixture);
                rows.add(new Object[]{id, 1L, 1L, 1L, 1L, start, fixture.end(), fixture.state().name(), "ADMIN", REFERENCE, REFERENCE});
            }
            batch(APPOINTMENT_INSERT, rows);
            jdbc.execute("SELECT setval(pg_get_serial_sequence('appointments','id'), (SELECT max(id) FROM appointments), true)");
        });
    }

    private void expandAssociations() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List<Object[]> rows = new ArrayList<>();
            var existing = new java.util.HashSet<>(links);
            for (long business = 1; business <= 20; business++) for (int member = 0; member < 20; member++) {
                for (int service = 2; service < 19; service++) {
                    Association link = new Association((business - 1) * 20 + member + 1, (business - 1) * 20 + service + 1);
                    if (existing.add(link)) {
                        links.add(link);
                        rows.add(new Object[]{link.staff(), link.service()});
                    }
                }
            }
            batch("INSERT INTO staff_service_offerings(staff_member_id,service_offering_id) VALUES (?,?)", rows);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_service_offerings", Long.class)).isEqualTo(7_220L);
    }

    private void verifyEquivalentData() throws Exception {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        fingerprints.put("originalAppointments", fingerprint("SELECT row_to_json(a)::text FROM appointments a WHERE id<=50000 ORDER BY id"));
        fingerprints.put("recentAppointments", fingerprint("SELECT row_to_json(a)::text FROM appointments a WHERE staff_member_id IN (1,20,400) AND ends_at>? ORDER BY id", REFERENCE.withHour(0)));
        fingerprints.put("referenceAssociations", fingerprint("SELECT row_to_json(a)::text FROM staff_service_offerings a WHERE service_offering_id IN (1,2,20) ORDER BY id"));
        if (variant.equals("v2-vacuumed")) referenceFingerprints.putAll(fingerprints);
        assertThat(fingerprints).containsExactlyInAnyOrderEntriesOf(referenceFingerprints);
        // Also check the exact recent result sets, not only their cardinalities.
        for (long staff : List.of(1L, 20L, 400L)) for (int days : List.of(1, 7)) {
            var ids = appointments.findBlockingAppointments((staff - 1) / 20 + 1, staff,
                    List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED), REFERENCE.withHour(0), REFERENCE.withHour(0).plusDays(days))
                    .stream().map(Appointment::getId).sorted().toList();
            String key = staff + ":" + days;
            referenceResults.putIfAbsent(key, ids);
            assertThat(ids).as("Unchanged recent Q05 results: %s", key).containsExactlyElementsOf(referenceResults.get(key));
        }
        write("equivalence.json", Map.of("sha256", fingerprints, "recentQ05Results", referenceResults,
                "equivalentToBaseline", true));
    }

    private String fingerprint(String sql, Object... parameters) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) row -> {
            digest.update(row.getString(1).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }, parameters);
        return HexFormat.of().formatHex(digest.digest());
    }

    private void measureAvailabilityScenarios(long staff) throws Exception {
        long business = (staff - 1) / 20 + 1;
        for (int days : List.of(1, 7)) measureAvailability(business, staff, REFERENCE.withHour(0), days);
        measureAvailability(business, staff, REFERENCE.plusYears(10).withHour(0), 1);
    }

    private void batch(String sql, List<Object[]> rows) {
        jdbc.batchUpdate(sql, rows, 500, (statement, row) -> {
            for (int index = 0; index < row.length; index++) statement.setObject(index + 1, row[index]);
        });
    }

    private void measureAgenda() throws Exception {
        var loads = jdbc.queryForList("SELECT business_id, count(*) AS n FROM appointments GROUP BY business_id ORDER BY n, business_id");
        long small = ((Number) loads.getFirst().get("business_id")).longValue();
        long large = ((Number) loads.getLast().get("business_id")).longValue();
        for (long business : List.of(small, large)) for (int days : List.of(1, 31)) for (boolean desc : List.of(false, true)) {
            for (Long staff : Arrays.asList(null, (business - 1) * 20 + 1)) {
                agenda(business, days, desc, staff, 0);
            }
        }
        long count = fixtures.stream().filter(f -> inAgenda(f, large, REFERENCE.withHour(0), REFERENCE.withHour(0).plusDays(31), null)).count();
        int deepPage = (int) ((count - 1) / 50);
        assertThat(deepPage).isPositive();
        agenda(large, 31, false, null, deepPage);
        agenda(large, 31, true, null, deepPage);
        agenda(large, 1, false, null, 0, REFERENCE.plusYears(10).withHour(0));
    }

    private void measureHistoricalAgenda() throws Exception {
        long business = 2;
        LocalDateTime from = REFERENCE.withHour(0).minusDays(30), to = from.plusDays(31);
        Map<String, Long> days = new LinkedHashMap<>();
        for (int day = 0; day < 31; day++) {
            LocalDateTime start = from.plusDays(day);
            long count = fixtures.stream().filter(f -> inAgenda(f, business, start, start.plusDays(1), null)).count();
            assertThat(count).as("Historical agenda activity on %s", start.toLocalDate()).isPositive();
            days.put(start.toLocalDate().toString(), count);
        }
        long total = fixtures.stream().filter(f -> inAgenda(f, business, from, to, null)).count();
        int deepPage = (int) ((total - 50) / 50);
        assertThat(deepPage).isPositive();
        assertThat(total - (long) deepPage * 50).isGreaterThanOrEqualTo(50);
        agenda(business, 31, false, null, 0, from, "historical31-");
        agenda(business, 31, false, null, deepPage, from, "historical31-");
        write("historical-agenda.json", Map.of("business", business, "from", from.toString(), "to", to.toString(),
                "activityByDay", days, "total", total, "deepPage", deepPage, "pageSize", 50));
    }

    private boolean inAgenda(Fixture f, long business, LocalDateTime from, LocalDateTime to, Long staff) {
        return f.business() == business && !f.start().isBefore(from) && f.start().isBefore(to)
                && (staff == null || f.staff() == staff);
    }

    private void agenda(long business, int days, boolean desc, Long staff, int page) throws Exception {
        agenda(business, days, desc, staff, page, REFERENCE.withHour(0));
    }

    private void agenda(long business, int days, boolean desc, Long staff, int page, LocalDateTime from) throws Exception {
        agenda(business, days, desc, staff, page, from, "");
    }

    private void agenda(long business, int days, boolean desc, Long staff, int page, LocalDateTime from, String label) throws Exception {
        LocalDateTime to = from.plusDays(days);
        var query = new AppointmentReadQuery(from, to, List.of(), staff, null, null, null, page, 50, desc,
                List.of(new BusinessLocalTimeRange.Window(from, to)));
        Comparator<Fixture> comparator = Comparator.comparing(Fixture::start);
        if (desc) comparator = comparator.reversed();
        List<Long> expected = fixtures.stream().filter(f -> inAgenda(f, business, from, to, staff))
                .sorted(comparator.thenComparingLong(Fixture::id)).map(Fixture::id).toList();
        List<Execution> captured = capture(() -> {
            var result = reads.list(business, ZONE, query);
            assertThat(result.page().totalElements()).isEqualTo(expected.size());
            assertThat(result.data().stream().map(dto -> dto.id()).toList())
                    .containsExactlyElementsOf(expected.stream().skip((long) page * 50).limit(50).toList());
        });
        assertThat(captured).hasSize(2);
        String scenario = label + "b" + business + "-d" + days + "-desc" + desc + "-staff" + staff + "-p" + page + "-" + from.toLocalDate();
        measure("Q02", scenario, captured.getFirst(), expected.size(), 1);
        measure("Q01", scenario, captured.getLast(), expected.size(), Math.min(50, Math.max(0, expected.size() - page * 50)));
    }

    private void measureAvailability(long business, long staff, LocalDateTime from, int days) throws Exception {
        LocalDateTime to = from.plusDays(days);
        List<Long> expected = fixtures.stream().filter(f -> f.business() == business && f.staff() == staff
                && (f.state() == AppointmentStatus.PENDING || f.state() == AppointmentStatus.CONFIRMED)
                && f.start().isBefore(to) && f.end().isAfter(from)).map(Fixture::id).toList();
        if (staff == 1 && from.equals(REFERENCE.withHour(0))) assertThat(expected).contains(50_000L);
        List<Execution> captured = capture(() -> assertThat(appointments.findBlockingAppointments(business, staff,
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED), from, to).stream().map(Appointment::getId).toList())
                .containsExactlyInAnyOrderElementsOf(expected));
        assertThat(captured).hasSize(1);
        measure("Q05", "staff" + staff + "-d" + days + "-" + from.toLocalDate(), captured.getFirst(), expected.size(), expected.size());
    }

    private void measureAssociations(long service) throws Exception {
        List<Long> expected = links.stream().filter(link -> link.service() == service).map(Association::staff).toList();
        List<Execution> captured = capture(() -> assertThat(associations.findAllByServiceOfferingId(service).stream()
                .map(link -> link.getStaffMemberId()).toList()).containsExactlyInAnyOrderElementsOf(expected));
        assertThat(captured).hasSize(1);
        measure("Q25", "service" + service, captured.getFirst(), expected.size(), expected.size());
    }

    private List<Execution> capture(Runnable action) {
        assertThat(CAPTURE.get()).isNull();
        CAPTURE.set(new ArrayList<>());
        try { action.run(); return List.copyOf(CAPTURE.get()); }
        finally { CAPTURE.remove(); }
    }

    private void measure(String query, String scenario, Execution execution, int cardinality, int outputRows) throws Exception {
        assertThat(execution.sql().stripLeading().toLowerCase()).startsWith("select");
        String signature = JSON.writeValueAsString(Map.of("sql", execution.sql(), "bindingsAndOptions", execution.calls().stream().map(Call::evidence).toList()));
        String key = query + ":" + scenario;
        if (variant.equals("v2-vacuumed") || variant.equals("critical-pending")) referenceExecutions.put(key, signature);
        if (variant.equals("q11-union-experiment")) referenceExecutions.putIfAbsent(key, signature);
        if (variant.equals("baseline") || variant.equals("candidate")) referenceExecutions.putIfAbsent(key, signature);
        assertThat(signature).as("Identical SQL and bindings for %s", key).isEqualTo(referenceExecutions.get(key));
        for (int sample = 0; sample <= SAMPLES; sample++) {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setReadOnly(true);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            JsonNode plan = transaction.execute(status -> {
                jdbc.execute("SET LOCAL statement_timeout = '120s'");
                jdbc.execute("SET LOCAL lock_timeout = '5s'");
                return explain(execution, null);
            });
            assertThat(plan).isNotNull();
            assertThat(plan.get(0).get("Plan").get("Actual Rows").asInt()).isEqualTo(outputRows);
            recordMeasurement(query, scenario, sample, execution, cardinality, plan, Map.of("expectedOutputRows", outputRows));
        }
    }

    private JsonNode explain(Execution execution, org.springframework.transaction.TransactionStatus rollbackStatus) {
        if (rollbackStatus != null) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(rollbackStatus.isRollbackOnly()).isTrue();
        }
        String options = rollbackStatus == null ? "ANALYZE, BUFFERS, SETTINGS, FORMAT JSON"
                : "ANALYZE, BUFFERS, SETTINGS, WAL, FORMAT JSON";
        return jdbc.execute((Connection connection) -> {
            verifyContainer(connection);
            try (PreparedStatement statement = connection.prepareStatement("EXPLAIN (" + options + ") " + execution.sql())) {
                for (Call call : execution.calls()) call.apply(statement);
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    try { return JSON.readTree(result.getString(1)); }
                    catch (java.io.IOException failure) { throw new SQLException("Cannot parse EXPLAIN JSON", failure); }
                }
            }
        });
    }

    private void recordMeasurement(String query, String scenario, int sample, Execution execution, int cardinality,
                                   JsonNode plan, Map<String, ?> verification) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("variant", variant); row.put("query", query); row.put("scenario", scenario); row.put("sample", sample);
        row.put("warmup", sample == 0); row.put("sql", execution.sql());
        row.put("bindingsAndOptions", execution.calls().stream().map(Call::evidence).toList());
        row.put("matchingRows", cardinality); row.put("plan", plan);
        row.putAll(verification);
        measurements.add(row);
        write(query + "-" + scenario + "-" + sample + ".json", row);
    }

    private void exportDatabaseEvidence() throws Exception {
        write("flyway-history.json", jdbc.queryForList("SELECT installed_rank,version,description,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank"));
        write("indexes.json", jdbc.queryForList("""
                SELECT t.relname AS table_name,x.relname AS index_name,am.amname AS method,
                  i.indisprimary,i.indisunique,i.indisvalid,i.indisready,i.indislive,i.indnkeyatts,i.indnatts,
                  pg_get_indexdef(i.indexrelid) AS definition,pg_get_expr(i.indpred,i.indrelid) AS predicate,
                  pg_get_expr(i.indexprs,i.indrelid) AS expressions,pg_relation_size(i.indexrelid) AS size_bytes
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_class x ON x.oid=i.indexrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_am am ON am.oid=x.relam
                WHERE n.nspname=current_schema() ORDER BY t.relname,x.relname
                """));
        write("pg-indexes.json", jdbc.queryForList("SELECT * FROM pg_indexes WHERE schemaname=current_schema() ORDER BY tablename,indexname"));
        write("constraints.json", jdbc.queryForList("SELECT conrelid::regclass::text AS table_name,conname,contype,conindid,pg_get_constraintdef(oid) AS definition FROM pg_constraint WHERE connamespace=current_schema()::regnamespace ORDER BY table_name,conname"));
        write("statistics.json", jdbc.queryForList("""
                SELECT s.tablename,s.attname,s.null_frac,s.avg_width,s.n_distinct,s.correlation,
                  s.most_common_vals::text,s.most_common_freqs::text,s.histogram_bounds::text
                FROM pg_stats s WHERE s.schemaname=current_schema()
                  AND s.attname NOT IN ('session_token_hash','token_hash')
                ORDER BY s.tablename,s.attname
                """));
        write("table-statistics.json", maintenanceSnapshot());
        write("settings.json", jdbc.queryForList("""
                SELECT name,setting,unit,source FROM pg_settings WHERE name IN
                ('server_version','TimeZone','shared_buffers','work_mem','effective_cache_size','random_page_cost',
                 'seq_page_cost','default_statistics_target','plan_cache_mode','jit','max_parallel_workers_per_gather',
                 'track_io_timing','autovacuum','block_size','fsync','synchronous_commit','full_page_writes') ORDER BY name
                """));
        Map<String, Object> dataset = new LinkedHashMap<>();
        for (String table : List.of("businesses", "customers", "staff_members", "service_offerings", "staff_service_offerings", "appointments"))
            dataset.put(table, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
        dataset.put("appointmentsByBusinessStaffState", jdbc.queryForList("SELECT business_id,staff_member_id,status,count(*) AS n,min(starts_at) AS first,max(starts_at) AS last FROM appointments GROUP BY business_id,staff_member_id,status ORDER BY business_id,staff_member_id,status"));
        dataset.put("associationsByService", jdbc.queryForList("SELECT s.id,count(a.id) AS n FROM service_offerings s LEFT JOIN staff_service_offerings a ON a.service_offering_id=s.id GROUP BY s.id ORDER BY s.id"));
        write("dataset-summary.json", dataset);
    }

    private void write(String name, Object value) throws Exception {
        JSON.writeValue(output.resolve(name).toFile(), value);
    }

    private void writeSummary() throws Exception {
        writeSummary("summary.csv", measurements);
        if (variant.equals("critical-pending")) {
            writeSummary("select-summary.csv", measurements.stream().filter(row -> !row.containsKey("rollbackVerified")).toList());
            writeSummary("delete-summary.csv", measurements.stream().filter(row -> row.containsKey("rollbackVerified")).toList());
        }
    }

    private void writeSummary(String file, List<Map<String, Object>> rows) throws Exception {
        StringBuilder csv = new StringBuilder("variant,query,scenario,sample,warmup,matching_rows,planning_ms,execution_ms,root_node,shared_hit,shared_read,temp_read,temp_written,heap_fetches\n");
        for (Map<String, Object> row : rows) {
            JsonNode explanation = ((JsonNode) row.get("plan")).get(0), root = explanation.get("Plan");
            csv.append(variant).append(',').append(row.get("query")).append(',').append(row.get("scenario")).append(',').append(row.get("sample")).append(',')
                    .append(row.get("warmup")).append(',').append(row.get("matchingRows")).append(',')
                    .append(explanation.path("Planning Time")).append(',').append(explanation.path("Execution Time")).append(',')
                    .append(root.path("Node Type").asText()).append(',').append(root.path("Shared Hit Blocks").asLong()).append(',')
                    .append(root.path("Shared Read Blocks").asLong()).append(',').append(root.path("Temp Read Blocks").asLong()).append(',')
                    .append(root.path("Temp Written Blocks").asLong()).append(',');
            var heapFetches = root.findValues("Heap Fetches");
            if (!heapFetches.isEmpty()) csv.append(heapFetches.stream().mapToLong(JsonNode::asLong).sum());
            csv.append('\n');
        }
        Files.writeString(output.resolve(file), csv);
    }

    private static String jdbcUrl() {
        String url = postgres.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "prepareThreshold=0";
    }

    private static void verifyContainer(Connection connection) throws SQLException {
        String expected = jdbcUrl();
        if (!postgres.isRunning() || !expected.equals(connection.getMetaData().getURL())
                || !"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())
                || connection.getMetaData().getDatabaseMajorVersion() != 16) {
            throw new SQLException("Refusing access outside the dedicated PostgreSQL 16 container");
        }
    }

    private record Fixture(long id, long business, long staff, LocalDateTime start, LocalDateTime end, AppointmentStatus state) { }
    private record Association(long staff, long service) { }
    private record Execution(String sql, List<Call> calls) { }

    private record Call(Method method, Object[] arguments) {
        void apply(Object target) throws SQLException {
            try { invoke(target, method, arguments); }
            catch (SQLException failure) { throw failure; }
            catch (Throwable failure) { throw new SQLException("Cannot replay JDBC binding", failure); }
        }
        Map<String, Object> evidence() {
            return Map.of("method", method.getName(), "arguments", Arrays.stream(arguments)
                    .map(value -> value == null ? "<null>" : value.toString()).toList(),
                    "javaTypes", Arrays.stream(arguments).map(value -> value == null ? "null" : value.getClass().getName()).toList());
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    /** Observe prepared SELECT and DML executions while delegating transaction control. */
    private static class CapturingDataSource extends DelegatingDataSource implements AutoCloseable {
        CapturingDataSource(DataSource delegate) { super(delegate); }
        @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException { return wrap(super.getConnection(user, password)); }
        private Connection wrap(Connection delegate) throws SQLException {
            try { verifyContainer(delegate); }
            catch (SQLException failure) {
                try { delegate.close(); } catch (SQLException closing) { failure.addSuppressed(closing); }
                throw failure;
            }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                Object result = invoke(delegate, method, args);
                if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement)
                    return wrapStatement(statement, (String) args[0]);
                return result;
            });
        }
        private PreparedStatement wrapStatement(PreparedStatement delegate, String sql) {
            Map<Integer, Call> bindings = new TreeMap<>();
            Map<String, Call> options = new LinkedHashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                // Delegate first: never record a setter that the real driver rejected.
                Object result = invoke(delegate, method, args);
                String name = method.getName();
                if (name.equals("clearParameters")) bindings.clear();
                if (name.startsWith("set") && args != null) {
                    if (args.length >= 2 && args[0] instanceof Integer position) bindings.put(position, new Call(method, args.clone()));
                    else if (List.of("setMaxRows", "setLargeMaxRows", "setFetchSize", "setQueryTimeout", "setFetchDirection").contains(name))
                        options.put(name, new Call(method, args.clone()));
                }
                if (CAPTURE.get() != null && (name.equals("executeQuery") || name.equals("executeUpdate")
                        || name.equals("executeLargeUpdate"))) {
                    List<Call> calls = new ArrayList<>(options.values());
                    calls.addAll(bindings.values());
                    CAPTURE.get().add(new Execution(sql, List.copyOf(calls)));
                }
                return result;
            });
        }
        @Override public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) closeable.close();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Appointment.class)
    @EnableJpaRepositories(basePackageClasses = AppointmentRepository.class)
    static class Configuration {
        @Bean static BeanPostProcessor captureDataSource() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof DataSource source && !(bean instanceof CapturingDataSource)
                            ? new CapturingDataSource(source) : bean;
                }
            };
        }
    }
}
