package com.turnero.api.service;
import java.time.*;
import com.turnero.api.context.CurrentBusinessContext;
import com.turnero.api.dto.*;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class AppointmentReadService {
    private final CurrentBusinessContext context;
    private final BusinessRepository businesses;
    private final AppointmentReadRepository appointments;
    public AppointmentReadService(CurrentBusinessContext context, BusinessRepository businesses, AppointmentReadRepository appointments) {
        this.context = context; this.businesses = businesses; this.appointments = appointments;
    }
    private ZoneId zone(long business) {
        return ZoneId.of(businesses.findById(business).orElseThrow(() -> new ResourceNotFoundException("Business not found")).getTimezone());
    }
    public AppointmentPageResponseDto list(MultiValueMap<String, String> params) {
        long business = context.getCurrentBusinessId(); var zone = zone(business);
        return appointments.list(business, zone, AppointmentReadQuery.parse(params, zone, Clock.systemUTC()));
    }
    public AppointmentReadDto find(long id) {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        long business = context.getCurrentBusinessId();
        return appointments.find(business, id, zone(business)).orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }
}
