package com.turnero.api.service;

import com.turnero.api.dto.AuthMeResponseDto;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.Business;
import com.turnero.api.model.User;
import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.AuthProvider;
import com.turnero.api.repository.BusinessRepository;
import com.turnero.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final GoogleIdentityService googleIdentityService;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final BusinessRepository businessRepository;

    @Override
    public String loginWithGoogle(String idToken, String ipAddress, String userAgent) {
        GoogleIdentityDto identity = googleIdentityService.verify(idToken);

        User user = userRepository.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, identity.subject())
                .orElseThrow(() -> new UnauthorizedException("User is not authorized"));

        if (user.getBusinessId() == null) {
            throw new ForbiddenException("User is not associated with a business");
        }

        return sessionService.createSession(user.getId(), ipAddress, userAgent);
    }

    @Override
    public AuthMeResponseDto getCurrentUser(String rawSessionToken) {
        UserSession session = sessionService.validateSession(rawSessionToken);

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (user.getBusinessId() == null) {
            throw new ForbiddenException("User is not associated with a business");
        }

        Business business = businessRepository.findById(user.getBusinessId())
                .orElseThrow(() -> new ForbiddenException("Business not found"));

        return new AuthMeResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                business.getId(),
                business.getName(),
                business.getSlug()
        );
    }
}
