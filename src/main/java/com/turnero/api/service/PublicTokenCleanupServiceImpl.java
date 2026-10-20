package com.turnero.api.service;

import com.turnero.api.config.PublicTokenCleanupProperties;
import com.turnero.api.model.Business;
import com.turnero.api.repository.AppointmentPublicTokenRepository;
import com.turnero.api.repository.BusinessRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PublicTokenCleanupServiceImpl implements PublicTokenCleanupService{

    private final AppointmentPublicTokenRepository tokenRepository;
    private final BusinessRepository businessRepository;
    private final PublicTokenCleanupProperties properties;
    private final Clock sessionCleanupClock;

    @Override
    public int cleanupPublicTokensBatch() {

        List<Business> businesses = businessRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        Instant now = sessionCleanupClock.instant();

        int remaining = properties.getBatchSize();
        int totalDeleted = 0;

        for (Business business : businesses) {

            if (remaining == 0) {
                break;
            }

            ZoneId businessZone = ZoneId.of(business.getTimezone());

            LocalDateTime cutoff = LocalDateTime.ofInstant(now, businessZone).minusDays(properties.getRetentionDays());

            List<Long> candidateIds = tokenRepository.findCleanupCandidateIds(business.getId(), cutoff,
                    PageRequest.of(0, remaining));

            if (candidateIds.isEmpty()) {
                continue;
            }

            int deleted = tokenRepository.deleteCleanupCandidatesByIds(business.getId(), candidateIds, cutoff);

            remaining -= candidateIds.size();
            totalDeleted += deleted;
        }

        return totalDeleted;

    }
}
