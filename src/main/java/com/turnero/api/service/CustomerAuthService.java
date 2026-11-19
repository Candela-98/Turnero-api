package com.turnero.api.service;

import com.turnero.api.dto.AuthBusinessResponseDto;
import com.turnero.api.dto.CustomerAuthResponseDto;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.exception.*;
import com.turnero.api.model.Business;
import com.turnero.api.model.Customer;
import com.turnero.api.model.User;
import com.turnero.api.model.enums.AuthProvider;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.model.enums.CustomerStatus;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

import static com.turnero.api.model.enums.SessionScope.CUSTOMER;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {
    private final GoogleIdentityService google;
    private final UserRepository users;
    private final BusinessRepository businesses;
    private final CustomerRepository customers;
    private final CustomerWriteLock customerWriteLock;
    private final SessionService sessions;
    private final JdbcTemplate jdbc;

    public record Login(String token, CustomerAuthResponseDto response) {}

    @Transactional
    public Login login(String slug, String idToken, String ip, String userAgent) {
        GoogleIdentityDto identity = google.verify(idToken);
        if (!identity.emailVerified() || identity.subject() == null || identity.subject().isBlank()
                || identity.email() == null || identity.email().isBlank()) {
            throw new UnauthorizedException("Invalid Google identity");
        }
        Business business = business(slug);
        business = customerWriteLock.acquire(business.getId());
        requireActive(business);
        User user = users.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, identity.subject()).orElse(null);
        Customer customer = user == null ? null
                : customers.findByBusinessIdAndUserId(business.getId(), user.getId()).orElse(null);
        if (customer == null) {
            String email = identity.email().trim().toLowerCase(Locale.ROOT);
            var matches = customers.findEmailMatches(business.getId(), email);
            if (matches.size() > 1) throw new CustomerIdentityAmbiguousException();
            if (!matches.isEmpty() && matches.getFirst().getUserId() != null) {
                throw new CustomerIdentityConflictException();
            }
            if (user == null) {
                // A concurrent login in another business may have inserted this global identity.
                jdbc.update("""
                        INSERT INTO users (name, email, auth_provider, auth_subject, role, created_at, updated_at)
                        VALUES (?, ?, 'GOOGLE', ?, 'CUSTOMER', LOCALTIMESTAMP, LOCALTIMESTAMP)
                        ON CONFLICT (auth_provider, auth_subject) DO NOTHING
                        """, email.substring(0, Math.min(email.length(), 120)), email, identity.subject());
                user = users.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, identity.subject()).orElseThrow();
            }
            LocalDateTime now = LocalDateTime.now();
            customer = matches.isEmpty() ? Customer.builder()
                    .businessId(business.getId()).name(user.getName()).email(identity.email().trim())
                    .status(CustomerStatus.ACTIVE).createdAt(now).updatedAt(now).build() : matches.getFirst();
            customer.setUserId(user.getId());
            customers.saveAndFlush(customer);
        }
        String token = sessions.createCustomerSession(user.getId(), customer.getId(), ip, userAgent);
        return new Login(token, response(business, customer));
    }

    @Transactional(readOnly = true)
    public CustomerAuthResponseDto me(String slug, String token) {
        Business business = business(slug);
        requireActive(business);
        return response(business, sessionCustomer(business, token));
    }

    @Transactional
    public void logout(String slug, String token) {
        Business business = business(slug);
        sessionCustomer(business, token);
        sessions.revokeSession(token, CUSTOMER);
    }

    private Customer sessionCustomer(Business business, String token) {
        var session = sessions.validateSession(token, CUSTOMER);
        Customer customer = customers.findByIdAndBusinessId(session.getCustomerId(), business.getId())
                .orElseThrow(() -> new ForbiddenException("Session belongs to another business"));
        if (!session.getUserId().equals(customer.getUserId())) throw new UnauthorizedException("Invalid session");
        return customer;
    }

    private Business business(String slug) {
        return businesses.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    private void requireActive(Business business) {
        if (business.getStatus() != BusinessStatus.ACTIVE) throw new ForbiddenException("Business is not active");
    }

    private CustomerAuthResponseDto response(Business business, Customer customer) {
        return new CustomerAuthResponseDto(
                new CustomerAuthResponseDto.Profile(customer.getId(), customer.getName(), customer.getEmail(),
                        customer.getPhoneNumber(), customer.getStatus(), customer.isPublicBookingBlocked()),
                new AuthBusinessResponseDto(business.getId(), business.getName(), business.getSlug(),
                        business.getOnboardingStatus()));
    }
}
