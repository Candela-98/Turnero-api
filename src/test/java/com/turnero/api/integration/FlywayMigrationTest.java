package com.turnero.api.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("turnero_test")
            .withUsername("turnero")
            .withPassword("turnero");

    @Test
    void shouldRunFlywayMigrationsOnPostgreSQL() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations("classpath:db/migration")
                .load();

        assertDoesNotThrow(flyway::migrate);
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
