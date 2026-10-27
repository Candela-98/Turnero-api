package com.turnero.api.integration;
import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.repository.AppointmentReadRepository;
import com.turnero.api.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test") @Testcontainers
class AppointmentReadPostgresIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AppointmentReadService reads;
    @Autowired AppointmentReadRepository repository;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    @MockitoBean CurrentBusinessContext context;
    long business, other, customer, staff, service;
    @BeforeEach void setup() {
        business = business("America/Argentina/Buenos_Aires"); other = business("UTC");
        customer = jdbc.queryForObject("INSERT INTO customers(business_id,name,status,public_booking_blocked,public_booking_block_reason,created_at,updated_at) VALUES (?, 'Cliente histórico','INACTIVE',true,'PRIVADO',now(),now()) RETURNING id", Long.class, business);
        staff = jdbc.queryForObject("INSERT INTO staff_members(business_id,name,status,deleted_at,created_at,updated_at) VALUES (?, 'Profesional histórico','INACTIVE',now(),now(),now()) RETURNING id", Long.class, business);
        service = jdbc.queryForObject("INSERT INTO service_offerings(business_id,name,status,duration_minutes,price_cents,deleted_at,created_at,updated_at) VALUES (?, 'Servicio histórico','INACTIVE',30,1000,now(),now(),now()) RETURNING id", Long.class,business);
        when(context.getCurrentBusinessId()).thenReturn(business);
    }
    long business(String zone) {
        return jdbc.queryForObject("INSERT INTO businesses(name,slug,timezone,status,onboarding_status,created_at,updated_at) VALUES ('Studio',?,?,'ACTIVE','READY',now(),now()) RETURNING id",Long.class,UUID.randomUUID().toString(),zone);
    }
    long appointment(long owner, String start, String state) {
        return jdbc.queryForObject("INSERT INTO appointments(business_id,customer_id,staff_member_id,service_offering_id,starts_at,ends_at,duration_minutes,price_cents,status,source,created_at,updated_at) VALUES (?,?,?,?,?::timestamp,?::timestamp + interval '1 hour',60,1000,?,'ADMIN',now(),now()) RETURNING id", Long.class,owner,customer,staff,service,start,start,state);
    }
    LinkedMultiValueMap<String,String> params(String... pairs) {
        var p=new LinkedMultiValueMap<String,String>(); for(int i=0;i<pairs.length;i+=2) p.add(pairs[i],pairs[i+1]); return p;
    }
    @Test void readsHistoricalReferencesMidnightAndNeverLeaksAnotherBusiness() {
        long id=appointment(business,"2026-12-31 23:30:00","PENDING");
        appointment(business,"2027-01-01 00:00:00","CONFIRMED");
        long foreign=appointment(other,"2026-12-31 23:30:00","CANCELLED");
        var result=reads.list(params("date","2026-12-31"));
        assertThat(result.data()).hasSize(1); var a=result.data().getFirst();
        assertThat(a.id()).isEqualTo(id); assertThat(a.customer().name()).isEqualTo("Cliente histórico");
        assertThat(a.staffMember().deletedAt()).isNotNull();
        assertThat(a.staffMember().available()).isTrue(); assertThat(a.serviceOffering().available()).isTrue();
        assertThat(a.startsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(-3));
        assertThat(a.endsAt().toLocalDate()).isEqualTo(LocalDate.parse("2027-01-01"));
        assertThat(reads.find(id)).isEqualTo(a);
        assertThatThrownBy(() -> reads.find(foreign)).isInstanceOf(com.turnero.api.exception.ResourceNotFoundException.class);
        when(context.getCurrentBusinessId()).thenReturn(other);
        var inconsistent=reads.find(foreign);
        assertThat(inconsistent.customer().available()).isFalse(); assertThat(inconsistent.customer().name()).isNull();
        assertThat(inconsistent.staffMember().name()).isNull(); assertThat(inconsistent.staffMember().deletedAt()).isNull(); assertThat(inconsistent.serviceOffering().name()).isNull();
    }
    @Test void stablePaginationFiltersAndExclusiveRange() {
        for(String status: List.of("PENDING","CONFIRMED","CANCELLED","COMPLETED","NO_SHOW")) appointment(business,"2026-10-20 07:15:00",status);
        appointment(business,"2026-10-21 00:00:00","PENDING");
        var p=params("from","2026-10-20T00:00:00-03:00","to","2026-10-21T00:00:00-03:00","size","2");
        var first=reads.list(p); assertThat(first.page().totalElements()).isEqualTo(5);
        p.set("page","1"); var second=reads.list(p);
        assertThat(second.data().getFirst().id()).isGreaterThan(first.data().getLast().id());
        p.set("size","100"); p.set("page","0"); p.add("status","PENDING,NO_SHOW"); p.add("status","CONFIRMED");
        p.set("staff_member_id",String.valueOf(staff)); p.set("customer_id",String.valueOf(customer));
        p.set("service_offering_id",String.valueOf(service)); p.set("source","ADMIN");
        assertThat(reads.list(p).data()).hasSize(3);
        p.set("source","PUBLIC_BOOKING"); assertThat(reads.list(p).data()).isEmpty();
    }
    @Test void timezoneResolutionUsesEarlierOffsetAndAdvancesGaps() {
        jdbc.update("UPDATE businesses SET timezone='America/New_York' WHERE id=?",business);
        long overlap=appointment(business,"2026-11-01 01:30:00","PENDING");
        long gap=appointment(business,"2026-03-08 02:30:00","PENDING");
        assertThat(reads.find(overlap).startsAt().toString()).isEqualTo("2026-11-01T01:30-04:00");
        assertThat(reads.find(gap).startsAt().toString()).isEqualTo("2026-03-08T03:30-04:00");
    }
    @Test void localSeedIsExecutableOnDisposableDatabase() throws Exception {
        postgres.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(Path.of("scripts/seed-local-agenda.sql").toAbsolutePath().toString()), "/tmp/seed-agenda.sql");
        var result=postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
            "-v", "business_id="+business, "-v", "agenda_date=2026-10-20", "-f", "/tmp/seed-agenda.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        assertThat(reads.list(params("date","2026-10-20")).page().totalElements()).isEqualTo(106);
    }
    @Test void measures100000AppointmentsOnDisposablePostgres() throws Exception {
        jdbc.update("""
            INSERT INTO appointments(business_id,customer_id,staff_member_id,service_offering_id,starts_at,ends_at,duration_minutes,price_cents,status,source,created_at,updated_at)
            SELECT CASE WHEN n%5=0 THEN ? ELSE ? END,?,?,?, timestamp '2026-01-01' + (n%365)*interval '1 day' + (n%24)*interval '1 hour',
              timestamp '2026-01-01' + (n%365)*interval '1 day' + (n%24)*interval '1 hour' + interval '30 minutes',30,1000,
              (ARRAY['PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW'])[n%5+1],'ADMIN',now(),now() FROM generate_series(1,100000) n
            """, other,business,customer,staff,service);
        jdbc.execute("ANALYZE appointments");
        StringBuilder report=new StringBuilder("PostgreSQL 16 / disposable Testcontainers; 100000 appointments across 2 businesses / 365 days; existing indexes only. Exact repository SQL below. Two JDBC statements/page (count + joined projection) at sizes 1, 50 and 100; service adds one business timezone lookup.\n");
        for (int size : List.of(1,50,100)) {
            clearInvocations(named);
            var query=AppointmentReadQuery.parse(params("date","2026-10-20", "size",String.valueOf(size)),ZoneId.of("America/Argentina/Buenos_Aires"),Clock.systemUTC());
            repository.list(business,ZoneId.of("America/Argentina/Buenos_Aires"),query);
            verify(named).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
            verify(named).query(anyString(), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<com.turnero.api.dto.AppointmentReadDto>>any());
        }
        for (var parameters : List.of(params("date","2026-10-20"), params("from","2026-10-01T00:00:00-03:00", "to","2026-11-01T00:00:00-03:00", "status","PENDING,CONFIRMED", "staff_member_id",String.valueOf(staff)), params("date","2026-10-20", "sort","starts_at,desc"))) {
            clearInvocations(named);
            repository.list(business,ZoneId.of("America/Argentina/Buenos_Aires"),AppointmentReadQuery.parse(parameters,ZoneId.of("America/Argentina/Buenos_Aires"),Clock.systemUTC()));
            var sql = org.mockito.ArgumentCaptor.forClass(String.class);
            var bindings = org.mockito.ArgumentCaptor.forClass(SqlParameterSource.class);
            verify(named).query(sql.capture(), bindings.capture(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<com.turnero.api.dto.AppointmentReadDto>>any());
            var countSql = org.mockito.ArgumentCaptor.forClass(String.class);
            var countBindings = org.mockito.ArgumentCaptor.forClass(SqlParameterSource.class);
            verify(named).queryForObject(countSql.capture(),countBindings.capture(),eq(Long.class));
            for(var statement:List.of(Map.entry(sql.getValue(),bindings.getValue()),Map.entry(countSql.getValue(),countBindings.getValue()))) {
                report.append("\n").append(statement.getKey()).append("\n").append(statement.getValue()).append("\n");
                for(var line:named.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + statement.getKey(),statement.getValue()))
                    report.append(line.values().iterator().next()).append("\n");
            }
        }
        Path path=Path.of("build/reports/agenda-performance.txt"); Files.createDirectories(path.getParent()); Files.writeString(path,report);
    }
}
