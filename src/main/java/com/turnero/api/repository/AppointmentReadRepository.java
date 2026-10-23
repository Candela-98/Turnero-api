package com.turnero.api.repository;

import com.turnero.api.dto.AppointmentReadDto;
import com.turnero.api.dto.AppointmentPageResponseDto;
import com.turnero.api.dto.CustomerPageResponseDto.PageInfo;
import com.turnero.api.model.enums.*;
import com.turnero.api.service.AppointmentReadQuery;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AppointmentReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = """
        SELECT a.*, c.id AS c_id, c.name AS c_name, c.email AS c_email, c.phone_number AS c_phone, c.status AS c_status,
            s.id AS s_id, s.name AS s_name, s.category AS s_category, s.status AS s_status,
            p.id AS p_id, p.name AS p_name, p.role_label AS p_role, p.avatar_url AS p_avatar, p.status AS p_status, p.deleted_at AS p_deleted_at
        FROM appointments a
        LEFT JOIN customers c ON c.id = a.customer_id AND c.business_id = a.business_id
        LEFT JOIN service_offerings s ON s.id = a.service_offering_id AND s.business_id = a.business_id
        LEFT JOIN staff_members p ON p.id = a.staff_member_id AND p.business_id = a.business_id
        """;
    public AppointmentPageResponseDto list(long businessId, ZoneId zone, AppointmentReadQuery query) {
        var params = new MapSqlParameterSource("business", businessId);
        var where = new StringBuilder(" WHERE a.business_id = :business AND (");
        if (query.windows().isEmpty()) where.append("1 = 0");
        for (int index = 0; index < query.windows().size(); index++) {
            if (index > 0) where.append(" OR ");
            where.append("(a.starts_at >= :from").append(index).append(" AND a.starts_at < :to").append(index).append(")");
            params.addValue("from" + index, query.windows().get(index).from()).addValue("to" + index, query.windows().get(index).to());
        }
        where.append(")");
        if (!query.statuses().isEmpty()) { where.append(" AND a.status IN (:statuses)"); params.addValue("statuses", query.statuses()); }
        filter(where, params, "staff_member_id", query.staffId());
        filter(where, params, "customer_id", query.customerId());
        filter(where, params, "service_offering_id", query.serviceId());
        filter(where, params, "source", query.source());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM appointments a" + where, params, Long.class);
        params.addValue("limit", query.size()).addValue("offset", (long) query.page() * query.size());
        var data = jdbc.query(SELECT + where + " ORDER BY a.starts_at " + (query.descending() ? "DESC" : "ASC")
            + ", a.id ASC LIMIT :limit OFFSET :offset", params, (rs, row) -> map(rs, zone));
        return new AppointmentPageResponseDto(data, new PageInfo(query.page(), query.size(), total, (int) ((total + query.size() - 1) / query.size())));
    }
    public Optional<AppointmentReadDto> find(long businessId, long id, ZoneId zone) {
        return jdbc.query(SELECT + " WHERE a.business_id = :business AND a.id = :id",
            new MapSqlParameterSource("business", businessId).addValue("id", id), (rs, row) -> map(rs, zone)).stream().findFirst();
    }
    private static void filter(StringBuilder where, MapSqlParameterSource params, String name, Object value) {
        if (value != null) { where.append(" AND a.").append(name).append(" = :").append(name); params.addValue(name, value); }
    }
    private static OffsetDateTime time(ResultSet rs, String name, ZoneId zone) throws SQLException {
        var local = rs.getObject(name, LocalDateTime.class);
        return local == null ? null : local.atZone(zone).toOffsetDateTime();
    }
    private static AppointmentReadDto map(ResultSet r, ZoneId zone) throws SQLException {
        return new AppointmentReadDto(r.getLong("id"), r.getLong("customer_id"), r.getLong("service_offering_id"), r.getLong("staff_member_id"),
            time(r,"starts_at",zone), time(r,"ends_at",zone), r.getInt("duration_minutes"), r.getInt("price_cents"),
            AppointmentStatus.valueOf(r.getString("status")), AppointmentSource.valueOf(r.getString("source")),
            r.getString("customer_notes"), r.getString("internal_notes"), r.getString("cancellation_reason"), time(r,"created_at",zone), time(r,"updated_at",zone),
            new AppointmentReadDto.CustomerReference(r.getLong("customer_id"), r.getObject("c_id") != null, r.getString("c_name"), r.getString("c_email"), r.getString("c_phone"), r.getString("c_status")),
            new AppointmentReadDto.ServiceReference(r.getLong("service_offering_id"), r.getObject("s_id") != null, r.getString("s_name"), r.getString("s_category"), r.getString("s_status")),
            new AppointmentReadDto.StaffReference(r.getLong("staff_member_id"), r.getObject("p_id") != null, r.getString("p_name"), r.getString("p_role"), r.getString("p_avatar"), r.getString("p_status"), time(r,"p_deleted_at",zone)));
    }
}
