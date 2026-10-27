package com.turnero.api.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero_test")
            .withUsername("turnero")
            .withPassword("turnero");

    @TempDir
    Path existingMigrations;

    private String schema;

    @BeforeEach
    void setUp() {
        schema = "flyway_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void shouldRunStructuralMigrationsAndCreateCleanupIndexesOnEmptyPostgreSQL() throws Exception {
        Flyway flyway = configuration().load();

        assertDoesNotThrow(flyway::migrate);
        assertSuccessfulVersions("1", "3", "4", "1002", "1003", "1004");
        assertCleanupIndexes();
        assertPublicTokenCleanupIndexes();
    }

    @Test
    void shouldUpgradeFromV1ToV3() throws Exception {
        assertDoesNotThrow(configuration().target("1").load()::migrate);
        assertSuccessfulVersions("1");
        assertThat(cleanupIndexes()).isEmpty();

        assertDoesNotThrow(configuration().target("3").load()::migrate);

        assertSuccessfulVersions("1", "3");
        assertCleanupIndexes();
    }

    @Test
    void shouldRunStructuralMigrationsBeforeSeedOnNewLocalDatabase() throws Exception {
        Flyway flyway = configuration()
                .locations("classpath:db/migration", "classpath:db/seed/local")
                .load();

        assertDoesNotThrow(flyway::migrate);
        assertSuccessfulVersions("1", "3", "4", "1000", "1001", "1002", "1003", "1004");
        assertCleanupIndexes();
        assertPublicTokenCleanupIndexes();
    }

    @Test
    void shouldApplyV3OutOfOrderAfterExistingV1AndV1000() throws Exception {
        // Reuse the real scripts to represent the migrations available before V3.
        copyMigration("db/migration/V1__initial_schema.sql");
        copyMigration("db/seed/local/V1000__seed_barber_studio.sql");
        Flyway previous = configuration()
                .locations("filesystem:" + existingMigrations.toAbsolutePath().toString().replace('\\', '/'))
                .load();
        assertDoesNotThrow(previous::migrate);
        assertSuccessfulVersions("1", "1000");
        assertThat(cleanupIndexes()).isEmpty();

        Flyway withoutOutOfOrder = configuration()
                .locations("classpath:db/migration", "classpath:db/seed/local")
                .load();
        assertThat(withoutOutOfOrder.validateWithResult().validationSuccessful).isFalse();

        Flyway updated = configuration()
                .locations("classpath:db/migration", "classpath:db/seed/local")
                .outOfOrder(true)
                .load();
        assertDoesNotThrow(updated::migrate);
        assertSuccessfulVersions("1", "1000", "3", "4", "1001", "1002", "1003", "1004");
        assertCleanupIndexes();
        assertPublicTokenCleanupIndexes();
        assertThat(updated.validateWithResult().validationSuccessful).isTrue();
        assertThat(updated.migrate().migrationsExecuted).isZero();
    }

    @Test
    void shouldUpgradeFromV3ToV4AndValidateRepeatedMigration() throws Exception {
        Flyway previous = configuration().target("3").load();
        assertDoesNotThrow(previous::migrate);
        assertSuccessfulVersions("1", "3");
        assertThat(cleanupIndexes("appointment_public_tokens")).isEmpty();

        Flyway updated = configuration().target("4").load();
        assertThat(updated.migrate().migrationsExecuted).isEqualTo(1);

        assertSuccessfulVersions("1", "3", "4");
        assertCleanupIndexes();
        assertPublicTokenCleanupIndexes();
        assertThat(updated.validateWithResult().validationSuccessful).isTrue();
        assertThat(updated.migrate().migrationsExecuted).isZero();
    }

    @Test
    void shouldPreserveArchivedCustomersAndDefaultPublicBookingsToAllowedOnUpgrade() throws Exception {
        configuration().locations("classpath:db/migration", "classpath:db/seed/local").target("1003").load().migrate();
        long id;
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            connection.setReadOnly(false);
            try (ResultSet rows = statement.executeQuery("INSERT INTO customers (business_id,name,phone_number,status,internal_notes,created_at,updated_at) VALUES (1,'Legacy','123','INACTIVE','Private note',NOW(),NOW()) RETURNING id")) {
                assertThat(rows.next()).isTrue(); id = rows.getLong(1);
            }
        }
        configuration().locations("classpath:db/migration", "classpath:db/seed/local").load().migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT status, public_booking_blocked, public_booking_block_reason FROM customers WHERE id=" + id)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("INACTIVE");
            assertThat(rows.getBoolean(2)).isFalse();
            assertThat(rows.getString(3)).isNull();
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND indexname IN ('idx_customers_business_normalized_email','idx_customers_business_normalized_phone','idx_customers_business_archive_block')")) {
            rows.next(); assertThat(rows.getInt(1)).isEqualTo(3);
        }
    }

    private FluentConfiguration configuration() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
    }

    private Connection connection() throws Exception {
        return connection(schema);
    }

    private Connection connection(String schemaName) throws Exception {
        Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schemaName);
        connection.setReadOnly(true);
        return connection;
    }

    private void copyMigration(String resource) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "Migration resource must exist: " + resource);
            Files.copy(stream, existingMigrations.resolve(Path.of(resource).getFileName()));
        }
    }

    private void assertSuccessfulVersions(String... expected) throws Exception {
        assertSuccessfulVersionsInSchema(schema, expected);
    }

    private void assertSuccessfulVersionsInSchema(String schemaName, String... expected) throws Exception {
        List<String> versions = new ArrayList<>();
        try (Connection connection = connection(schemaName);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT version, success
                     FROM flyway_schema_history
                     WHERE version IS NOT NULL
                     ORDER BY installed_rank
                     """)) {
            while (rows.next()) {
                versions.add(rows.getString("version"));
                assertThat(rows.getBoolean("success")).isTrue();
            }
        }
        assertThat(versions).containsExactly(expected);
    }

    private List<CleanupIndex> cleanupIndexes() throws Exception {
        return cleanupIndexes("user_sessions");
    }

    private List<CleanupIndex> cleanupIndexes(String tableName) throws Exception {
        List<CleanupIndex> indexes = new ArrayList<>();
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT c.relname AS index_name, am.amname AS method,
                            i.indnkeyatts AS key_count, i.indnatts AS column_count,
                            pg_get_indexdef(i.indexrelid, 1, true) AS column_name,
                            pg_get_expr(i.indpred, i.indrelid) AS predicate,
                            i.indisvalid, i.indisready, i.indisunique
                     FROM pg_index i
                     JOIN pg_class c ON c.oid = i.indexrelid
                     JOIN pg_class t ON t.oid = i.indrelid
                     JOIN pg_namespace n ON n.oid = t.relnamespace
                     JOIN pg_am am ON am.oid = c.relam
                     WHERE n.nspname = current_schema()
                       AND t.relname = '%s'
                       AND c.relname IN (
                           'idx_user_sessions_cleanup_expires_at',
                           'idx_user_sessions_cleanup_revoked_at',
                           'idx_public_tokens_cleanup_expires',
                           'idx_public_tokens_cleanup_used'
                       )
                     ORDER BY c.relname
                     """.formatted(tableName))) {
            while (rows.next()) {
                indexes.add(new CleanupIndex(
                        rows.getString("index_name"), rows.getString("column_name"),
                        rows.getString("predicate").replace("(", "").replace(")", "").trim()));
                assertThat(rows.getString("method")).isEqualTo("btree");
                assertThat(rows.getInt("key_count")).isEqualTo(1);
                assertThat(rows.getInt("column_count")).isEqualTo(1);
                assertThat(rows.getBoolean("indisvalid")).isTrue();
                assertThat(rows.getBoolean("indisready")).isTrue();
                assertThat(rows.getBoolean("indisunique")).isFalse();
            }
        }
        return indexes;
    }

    private void assertCleanupIndexes() throws Exception {
        assertThat(cleanupIndexes()).containsExactly(
                new CleanupIndex("idx_user_sessions_cleanup_expires_at", "expires_at", "revoked_at IS NULL"),
                new CleanupIndex("idx_user_sessions_cleanup_revoked_at", "revoked_at", "revoked_at IS NOT NULL"));
    }

    private void assertPublicTokenCleanupIndexes() throws Exception {
        assertThat(cleanupIndexes("appointment_public_tokens")).containsExactly(
                new CleanupIndex("idx_public_tokens_cleanup_expires", "expires_at", "used_at IS NULL"),
                new CleanupIndex("idx_public_tokens_cleanup_used", "used_at", "used_at IS NOT NULL"));
    }

    private record CleanupIndex(String name, String column, String predicate) {
    }

    @Test
    void localSeedRepairAllowsGeneratedIdsAndPreservesExistingRows() throws Exception {
        verifySeedRepair("fresh_seed", false);
        verifySeedRepair("existing_seed", true);
    }


    @Test
    void removalMigrationUpgradesExistingSeedAndPreservesAppointmentForeignKey() throws Exception {
        String schema = "catalog_removal";
        seedFlyway(schema, "1001").migrate();
        assertSuccessfulVersionsInSchema(schema, "1", "3", "4", "1000", "1001");
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    INSERT INTO customers (id,business_id,name,status,created_at,updated_at)
                    VALUES (1,1,'Cliente histórico','ACTIVE',NOW(),NOW())
                    """);
                statement.executeUpdate("""
                    INSERT INTO appointments (business_id,customer_id,service_offering_id,staff_member_id,
                        starts_at,ends_at,duration_minutes,price_cents,status,source,created_at,updated_at)
                    VALUES (1,1,1,1,NOW(),NOW()+interval '30 minutes',30,800000,'CONFIRMED','ADMIN',NOW(),NOW())
                    """);
                seedFlyway(schema, "1002").migrate();
                assertSuccessfulVersionsInSchema(schema, "1", "3", "4", "1000", "1001", "1002");
                statement.executeUpdate("UPDATE service_offerings SET status='INACTIVE',deleted_at=NOW() WHERE id=1");
                try (var result = statement.executeQuery("""
                    SELECT s.name,s.deleted_at,a.price_cents FROM appointments a
                    JOIN service_offerings s ON s.id=a.service_offering_id WHERE s.id=1
                    """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("Corte clásico");
                    assertThat(result.getTimestamp(2)).isNotNull();
                    assertThat(result.getInt(3)).isEqualTo(800000);
                }
            }
        }
    }

    private void verifySeedRepair(String schema, boolean existingData) throws Exception {
        Flyway beforeRepair = seedFlyway(schema, "1000");
        beforeRepair.migrate();
        assertSuccessfulVersionsInSchema(schema, "1", "3", "4", "1000");
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                if (existingData) {
                    statement.executeUpdate("""
                            INSERT INTO service_offerings (id,business_id,name,duration_minutes,price_cents,status,created_at,updated_at)
                            VALUES (100,1,'Servicio existente',30,1000,'ACTIVE',NOW(),NOW())
                            """);
                    // A sequence may already be ahead due to previous writes or failed inserts.
                    statement.execute("SELECT setval('users_id_seq', 200, true)");
                }
                seedFlyway(schema, "1001").migrate();
                assertSuccessfulVersionsInSchema(schema, "1", "3", "4", "1000", "1001");
                var ids = java.util.Map.of("businesses", 2L, "users", existingData ? 201L : 2L,
                        "staff_members", 3L, "service_offerings", existingData ? 101L : 4L);
                for (var entry : ids.entrySet()) {
                    try (var result = statement.executeQuery("SELECT nextval(pg_get_serial_sequence('" + entry.getKey() + "', 'id'))")) {
                        result.next();
                        assertThat(result.getLong(1)).as(entry.getKey()).isEqualTo(entry.getValue());
                    }
                }
                try (var result = statement.executeQuery("""
                        INSERT INTO service_offerings (business_id,name,category,duration_minutes,price_cents,status,created_at,updated_at)
                        VALUES (1,'Depilación','Depilación',60,4000000,'ACTIVE',NOW(),NOW()) RETURNING id
                        """)) {
                    result.next();
                    assertThat(result.getLong(1)).isEqualTo(existingData ? 102 : 5);
                }
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM service_offerings")) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(existingData ? 5 : 4);
                }
            }
        }
    }

    private Flyway seedFlyway(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).target(target)
                .locations("classpath:db/migration", "classpath:db/seed/local").load();
    }
}
