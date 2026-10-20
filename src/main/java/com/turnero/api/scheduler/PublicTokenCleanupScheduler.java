package com.turnero.api.scheduler;

import com.turnero.api.service.PublicTokenCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PublicTokenCleanupScheduler {

    private final PublicTokenCleanupService publicTokenCleanupService;

    @Scheduled(cron = "${public-token.cleanup.cron}")
    public void cleanupPublicTokens() {

        long startedAt = System.nanoTime();

        int deletedCount =
                publicTokenCleanupService.cleanupPublicTokensBatch();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                Math.max(0L, System.nanoTime() - startedAt)
        );

        log.info(
                "Public token cleanup completed: deletedCount={}, durationMs={}",
                deletedCount,
                durationMs
        );
    }
}
