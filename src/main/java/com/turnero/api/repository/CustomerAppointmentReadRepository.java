package com.turnero.api.repository;

import com.turnero.api.dto.CustomerAppointmentPageResponseDto;
import com.turnero.api.dto.CustomerAppointmentReadDto;
import com.turnero.api.dto.CustomerAppointmentReadDto.NamedReference;
import com.turnero.api.dto.CustomerPageResponseDto.PageInfo;
import com.turnero.api.model.enums.AppointmentStatus;
import com.turnero.api.service.CustomerAppointmentReadQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Repository
@RequiredArgsConstructor
public class CustomerAppointmentReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String OWNED = """
            WHERE a.business_id = :business AND a.owner_user_id = :user
            """;
    private static final String SELECT = """
            SELECT a.id, s.id AS service_id, p.id AS staff_id, a.starts_at, a.ends_at, a.status,
                   s.name AS service_name, p.name AS staff_name
            FROM appointments a
            LEFT JOIN service_offerings s ON s.id = a.service_offering_id AND s.business_id = a.business_id
            LEFT JOIN staff_members p ON p.id = a.staff_member_id AND p.business_id = a.business_id
            """;

    public CustomerAppointmentPageResponseDto list(long businessId, long userId, ZoneId zone,
                                                   CustomerAppointmentReadQuery query) {
        var params = new MapSqlParameterSource("business", businessId).addValue("user", userId);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM appointments a " + OWNED, params, Long.class);
        params.addValue("limit", query.size()).addValue("offset", (long) query.page() * query.size());
        String order = query.descending() ? "DESC" : "ASC";
        var data = jdbc.query(SELECT + OWNED + " ORDER BY a.starts_at " + order
                        + ", a.id ASC LIMIT :limit OFFSET :offset", params, (row, index) ->
                new CustomerAppointmentReadDto(row.getLong("id"),
                        new NamedReference(row.getObject("service_id", Long.class), row.getString("service_name")),
                        new NamedReference(row.getObject("staff_id", Long.class), row.getString("staff_name")),
                        row.getObject("starts_at", LocalDateTime.class).atZone(zone).toOffsetDateTime(),
                        row.getObject("ends_at", LocalDateTime.class).atZone(zone).toOffsetDateTime(),
                        AppointmentStatus.valueOf(row.getString("status"))));
        int pages = (int) ((total + query.size() - 1) / query.size());
        return new CustomerAppointmentPageResponseDto(data, new PageInfo(query.page(), query.size(), total, pages));
    }
}
