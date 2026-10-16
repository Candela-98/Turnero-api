package com.turnero.api.service;

import com.turnero.api.config.SessionCleanupProperties;
import com.turnero.api.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionCleanupServiceImpl implements SessionCleanupService {

    private final UserSessionRepository userSessionRepository;
    private final SessionCleanupProperties sessionCleanupProperties;
    private final Clock sessionCleanupClock;

    @Override
    @Transactional
    public int cleanupSessionsBatch() {
        LocalDateTime now = LocalDateTime.now(sessionCleanupClock);
        LocalDateTime cutoff = now.minusDays(sessionCleanupProperties.getRetentionDays());
        PageRequest pageable = PageRequest.of(0, sessionCleanupProperties.getBatchSize());
        List<Long> ids = userSessionRepository.findCleanupCandidateIds(cutoff, pageable);

        if (ids.isEmpty()) {
            return 0;
        }

        return userSessionRepository.deleteCleanupCandidatesByIds(ids, cutoff);
    }
}
