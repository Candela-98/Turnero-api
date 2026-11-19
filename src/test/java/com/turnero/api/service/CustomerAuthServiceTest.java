package com.turnero.api.service;

import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.Business;
import com.turnero.api.model.Customer;
import com.turnero.api.model.User;
import com.turnero.api.model.enums.BusinessStatus;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.CustomerRepository;
import com.turnero.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static com.turnero.api.model.enums.AuthProvider.GOOGLE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CustomerAuthServiceTest {
    @Mock GoogleIdentityService google;
    @Mock UserRepository users;
    @Mock BusinessRepository businesses;
    @Mock CustomerRepository customers;
    @Mock CustomerWriteLock customerWriteLock;
    @Mock SessionService sessions;
    @Mock JdbcTemplate jdbc;
    @InjectMocks CustomerAuthService auth;

    @Test
    void verifiesGoogleBeforeAcquiringTheBusinessWriteLock() {
        Business business = Business.builder().id(1L).slug("studio").status(BusinessStatus.ACTIVE).build();
        User user = User.builder().id(10L).build();
        Customer customer = Customer.builder().id(20L).businessId(1L).userId(10L).build();
        given(google.verify("credential")).willReturn(new GoogleIdentityDto("subject", "verified@example.test", true));
        given(businesses.findBySlug("studio")).willReturn(Optional.of(business));
        given(customerWriteLock.acquire(1L)).willReturn(business);
        given(users.findByAuthProviderAndAuthSubject(GOOGLE, "subject")).willReturn(Optional.of(user));
        given(customers.findByBusinessIdAndUserId(1L, 10L)).willReturn(Optional.of(customer));

        auth.login("studio", "credential", null, null);

        var order = inOrder(google, businesses, customerWriteLock, users, sessions);
        order.verify(google).verify("credential");
        order.verify(businesses).findBySlug("studio");
        order.verify(customerWriteLock).acquire(1L);
        order.verify(users).findByAuthProviderAndAuthSubject(GOOGLE, "subject");
        order.verify(sessions).createCustomerSession(10L, 20L, null, null);
    }

    @Test
    void invalidGoogleTokenNeverAcquiresAWriteLockOrAccessesTheDatabase() {
        given(google.verify("invalid")).willThrow(new UnauthorizedException("Invalid Google ID token"));
        assertThatThrownBy(() -> auth.login("studio", "invalid", null, null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(businesses, customerWriteLock, users, customers, sessions, jdbc);
    }

    @Test
    void unverifiedEmailNeverAcquiresAWriteLockOrAccessesTheDatabase() {
        given(google.verify("unverified")).willReturn(new GoogleIdentityDto("subject", "unverified@example.test", false));
        assertThatThrownBy(() -> auth.login("studio", "unverified", null, null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(businesses, customerWriteLock, users, customers, sessions, jdbc);
    }
}
