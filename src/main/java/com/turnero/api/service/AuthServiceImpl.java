package com.turnero.api.service;

import com.turnero.api.dto.AuthBusinessResponseDto;
import com.turnero.api.dto.AuthSessionResponseDto;
import com.turnero.api.dto.AuthUserResponseDto;
import com.turnero.api.dto.GoogleIdentityDto;
import com.turnero.api.exception.ForbiddenException;
import com.turnero.api.exception.UnauthorizedException;
import com.turnero.api.model.Business;
import com.turnero.api.model.User;
import com.turnero.api.model.UserSession;
import com.turnero.api.model.enums.AuthProvider;
import com.turnero.api.model.enums.UserRole;
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
    public AuthLoginResult loginWithGoogle(String idToken, String ipAddress, String userAgent) {
        GoogleIdentityDto identity = googleIdentityService.verify(idToken);

        User user = userRepository.findByAuthProviderAndAuthSubject(AuthProvider.GOOGLE, identity.subject())
                .orElseThrow(() -> new UnauthorizedException("User is not authorized"));

        AuthSessionResponseDto session = resolveAuthorizedSession(user);
        String sessionToken = sessionService.createSession(user.getId(), ipAddress, userAgent);
        return new AuthLoginResult(sessionToken, session);
    }

    @Override
    public AuthSessionResponseDto getCurrentUser(String rawSessionToken) {
        UserSession session = sessionService.validateSession(rawSessionToken);

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return resolveAuthorizedSession(user);
    }

    @Override
    public void logout(String sessionToken) {
        sessionService.revokeSession(sessionToken);
    }

    private AuthSessionResponseDto resolveAuthorizedSession(User user) {
        if (user.getBusinessId() == null) {
            throw new ForbiddenException("User is not associated with a business");
        }
        if (user.getRole() != UserRole.OWNER) {
            throw new ForbiddenException("User is not allowed to access admin endpoints");
        }

        Business business = businessRepository.findById(user.getBusinessId())
                .orElseThrow(() -> new ForbiddenException("Business not found"));

        return new AuthSessionResponseDto(
                new AuthUserResponseDto(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getAvatarUrl()),
                new AuthBusinessResponseDto(business.getId(), business.getName(), business.getSlug(), business.getOnboardingStatus())
        );
    }
}
