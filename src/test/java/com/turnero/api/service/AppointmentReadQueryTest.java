package com.turnero.api.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class AppointmentReadQueryTest {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T01:00:00Z"), ZoneOffset.UTC);
    @Test void defaultDayUsesBusinessZoneAndAllStatuses() {
        var q = AppointmentReadQuery.parse(new LinkedMultiValueMap<>(), ZONE, CLOCK);
        assertThat(q.from()).isEqualTo(LocalDateTime.parse("2026-12-31T00:00:00"));
        assertThat(q.to()).isEqualTo(LocalDateTime.parse("2027-01-01T00:00:00"));
        assertThat(q.size()).isEqualTo(50); assertThat(q.statuses()).isEmpty();
    }
    @Test void combinesRepeatedAndCsvStatesAndAllFilters() {
        var p = new LinkedMultiValueMap<String,String>();
        p.add("status", "PENDING,CONFIRMED"); p.add("status", "NO_SHOW"); p.add("source", "ADMIN");
        p.add("staff_member_id", "2"); p.add("customer_id", "3"); p.add("service_offering_id", "4");
        p.add("sort", "starts_at,desc"); p.add("page", "2"); p.add("size", "100");
        var q = AppointmentReadQuery.parse(p, ZONE, CLOCK);
        assertThat(q.statuses()).containsExactly("PENDING","CONFIRMED","NO_SHOW");
        assertThat(q.staffId()).isEqualTo(2); assertThat(q.customerId()).isEqualTo(3); assertThat(q.serviceId()).isEqualTo(4);
        assertThat(q.descending()).isTrue(); assertThat(q.page()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"date=2026-02-30", "date=bad", "from=2026-01-01T00:00:00", "page=-1", "size=0", "size=101", "page=abc", "sort=id,asc", "status=UNKNOWN", "status=PENDING,", "source=WEB", "staff_member_id=0", "customer_id=-1", "service_offering_id=x"})
    void rejectsInvalidParameters(String input) {
        var p = new LinkedMultiValueMap<String,String>(); var parts=input.split("=",2); p.add(parts[0],parts[1]);
        assertThatThrownBy(() -> AppointmentReadQuery.parse(p,ZONE,CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rangeRespectsInstantOffsetsDuringOverlapAndGap() {
        var zone = ZoneId.of("America/New_York");
        assertThat(BusinessLocalTimeRange.windows(Instant.parse("2026-11-01T06:00:00Z"), Instant.parse("2026-11-01T06:45:00Z"), zone)).isEmpty();
        var windows = BusinessLocalTimeRange.windows(Instant.parse("2026-11-01T05:15:00Z"), Instant.parse("2026-11-01T06:15:00Z"), zone);
        assertThat(windows).containsExactly(new BusinessLocalTimeRange.Window(LocalDateTime.parse("2026-11-01T01:15:00"), LocalDateTime.parse("2026-11-01T02:00:00")));
        var gap = BusinessLocalTimeRange.windows(Instant.parse("2026-03-08T07:15:00Z"), Instant.parse("2026-03-08T07:45:00Z"), zone);
        assertThat(gap).hasSize(2);
        assertThat(gap.getFirst().from()).isEqualTo(LocalDateTime.parse("2026-03-08T02:15:00"));
        assertThat(gap.getLast().from()).isEqualTo(LocalDateTime.parse("2026-03-08T03:15:00"));
    }
    @Test void validatesRangesAndConvertsOffsets() {
        var p = new LinkedMultiValueMap<String,String>();
        p.add("from","2026-10-01T03:00:00Z"); p.add("to","2026-11-01T03:00:00Z");
        assertThat(AppointmentReadQuery.parse(p,ZONE,CLOCK).from()).isEqualTo(LocalDateTime.parse("2026-10-01T00:00:00"));
        p.set("to","2026-11-01T03:00:01Z");
        assertThatThrownBy(() -> AppointmentReadQuery.parse(p,ZONE,CLOCK)).isInstanceOf(IllegalArgumentException.class);
        p.set("to","2026-10-01T03:00:00Z");
        assertThatThrownBy(() -> AppointmentReadQuery.parse(p,ZONE,CLOCK)).isInstanceOf(IllegalArgumentException.class);
        p.set("date","2026-10-01");
        assertThatThrownBy(() -> AppointmentReadQuery.parse(p,ZONE,CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }
}
