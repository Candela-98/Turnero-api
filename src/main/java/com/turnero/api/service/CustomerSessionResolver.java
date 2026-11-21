package com.turnero.api.service;

import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.ResourceNotFoundException;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.Business;
import com.turnero.api.model.Customer;
import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.turnero.api.model.enums.SessionScope.CUSTOMER;

@Component
@RequiredArgsConstructor
public class CustomerSessionResolver {
    private final BusinessRepository businesses;
    private final CustomerRepository customers;
    private final SessionService sessions;

    public record Context(Business business, Customer customer, UserSession session) {}

    public Context requireActive(String slug, String token) {
        Business business = business(slug);
        if (business.getStatus() != BusinessStatus.ACTIVE) throw new ForbiddenException("Business is not active");
        return resolve(business, token);
    }

    public Context resolve(String slug, String token) {
        return resolve(business(slug), token);
    }

    private Context resolve(Business business, String token) {
        UserSession session = sessions.validateSession(token, CUSTOMER);
        Customer customer = customers.findByIdAndBusinessId(session.getCustomerId(), business.getId())
                .orElseThrow(() -> new ForbiddenException("Session belongs to another business"));
        if (!session.getUserId().equals(customer.getUserId())) throw new UnauthorizedException("Invalid session");
        return new Context(business, customer, session);
    }

    private Business business(String slug) {
        return businesses.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }
}
