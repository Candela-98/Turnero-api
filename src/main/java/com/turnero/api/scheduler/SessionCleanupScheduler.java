package com.turnero.api.scheduler;

import com.turnero.api.service.SessionCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupScheduler {

    private final SessionCleanupService sessionCleanupService;

    @Scheduled(cron = "${auth.session.cleanup.cron}")
    public void cleanupSessions() {
        long startedAt = System.nanoTime();
        int deletedCount = sessionCleanupService.cleanupSessionsBatch();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAt));

        log.info("Session cleanup completed: deletedCount={}, durationMs={}", deletedCount, durationMs);
    }
}
