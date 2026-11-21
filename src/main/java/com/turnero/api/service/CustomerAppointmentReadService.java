package com.turnero.api.service;

import com.turnero.api.dto.CustomerAppointmentPageResponseDto;
import com.turnero.api.repository.CustomerAppointmentReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class CustomerAppointmentReadService {
    private final CustomerSessionResolver customerSessions;
    private final CustomerAppointmentReadRepository appointments;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerAppointmentPageResponseDto list(String slug, String token, MultiValueMap<String, String> params) {
        var context = customerSessions.requireActive(slug, token);
        var query = CustomerAppointmentReadQuery.parse(params);
        return appointments.list(context.business().getId(), context.session().getUserId(),
                ZoneId.of(context.business().getTimezone()), query);
    }
}
