package com.turnero.api.ratelimit;

import com.turnero.api.config.PublicRateLimitProperties;
import com.turnero.api.exception.PublicRateLimitExceededException;
import com.turnero.api.exception.PublicRateLimitStorageFullException;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class PublicRateLimiterTest {
    private final ManualClock clock = new ManualClock();

    static PublicRateLimitProperties properties(int globalCapacity, int businessCapacity) {
        var p = new PublicRateLimitProperties();
        p.setEnabled(true);
        p.setRead(limit(globalCapacity));
        p.setWrite(limit(globalCapacity));
        var business = new PublicRateLimitProperties.BusinessLimits();
        business.setRead(limit(businessCapacity));
        business.setWrite(limit(businessCapacity));
        p.setBusiness(business);
        return p;
    }

    static PublicRateLimitProperties.LimitConfig limit(int capacity) {
        var config = new PublicRateLimitProperties.LimitConfig();
        config.setCapacity(capacity);
        config.setRefillTokens(capacity);
        config.setRefillPeriod(Duration.ofMinutes(1));
        return config;
    }

    private PublicRateLimiter limiter(PublicRateLimitProperties p) {
        return new PublicRateLimiter(new PublicRateLimitBucketStore(p, clock), p);
    }

    @Test
    void businessRejectionsDoNotConsumeGlobalPermits() {
        var limiter = limiter(properties(2, 1));
        limiter.check("client", false, "a");
        assertThatThrownBy(() -> limiter.check("client", false, "a"))
                .isInstanceOf(PublicRateLimitExceededException.class);
        limiter.check("client", false, "b");
        assertThatThrownBy(() -> limiter.check("client", false, "c"))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }

    @Test
    void globalRejectionsDoNotConsumeBusinessPermits() {
        var p = properties(1, 1);
        p.getRead().setRefillPeriod(Duration.ofSeconds(1));
        var limiter = limiter(p);
        limiter.check("client", false, "a");
        assertThatThrownBy(() -> limiter.check("client", false, "b"))
                .isInstanceOf(PublicRateLimitExceededException.class);
        clock.advance(Duration.ofSeconds(1));
        limiter.check("client", false, "b");
    }

    @Test
    void clientsAndReadWriteFamiliesAreIndependent() {
        var limiter = limiter(properties(1, 1));
        limiter.check("one", false, "a");
        assertThatThrownBy(() -> limiter.check("one", false, "a"))
                .isInstanceOf(PublicRateLimitExceededException.class);
        limiter.check("two", false, "a");
        limiter.check("one", true, "a");
        assertThatThrownBy(() -> limiter.check("one", true, "a"))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }

    @Test
    void cancellationAndBookingShareGlobalQuotaWithoutTokenKeys() {
        var limiter = limiter(properties(2, 1));
        limiter.check("client", false, null);
        limiter.check("client", false, "a");
        assertThatThrownBy(() -> limiter.check("client", false, null))
                .isInstanceOf(PublicRateLimitExceededException.class);
        limiter.check("client", true, null);
    }

    @Test
    void retryAfterRoundsUpAndRefillUsesControlledTime() {
        var limiter = limiter(properties(1, 1));
        limiter.check("client", false, null);
        clock.advance(Duration.ofMillis(500));
        assertThatThrownBy(() -> limiter.check("client", false, null))
                .isInstanceOfSatisfying(PublicRateLimitExceededException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(60));
        clock.advance(Duration.ofMillis(59_500));
        limiter.check("client", false, null);
    }

    @Test
    void inactiveButDepletedBucketsCannotExpireOrBeEvictedByNewKeys() {
        var p = properties(1, 1);
        p.setMaxBuckets(2);
        p.setIdleRetention(Duration.ofSeconds(1));
        var limiter = limiter(p);
        limiter.check("one", false, null);
        limiter.check("two", false, null);
        clock.advance(Duration.ofSeconds(2));
        for (int i = 0; i < 20; i++) {
            String ip = "new-" + i;
            assertThatThrownBy(() -> limiter.check(ip, false, null))
                    .isInstanceOf(PublicRateLimitStorageFullException.class);
        }
        assertThatThrownBy(() -> limiter.check("one", false, null))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }

    @Test
    void inactiveFullyRefilledBucketsCanBeRemovedSafely() {
        var p = properties(1, 1);
        p.setMaxBuckets(2);
        p.setIdleRetention(Duration.ofSeconds(1));
        var limiter = limiter(p);
        limiter.check("one", false, null);
        limiter.check("two", false, null);
        clock.advance(Duration.ofMinutes(1));
        limiter.check("three", false, null);
        // Same allowance as retaining the old, now-full bucket.
        limiter.check("one", false, null);
        assertThatThrownBy(() -> limiter.check("one", false, null))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }

    @Test
    void saturationDoesNotPartiallyConsumeAnExistingGlobalBucket() {
        var p = properties(2, 1);
        p.setMaxBuckets(2);
        var limiter = limiter(p);
        limiter.check("one", false, null);
        limiter.check("two", false, null);
        assertThatThrownBy(() -> limiter.check("one", false, "new-business"))
                .isInstanceOf(PublicRateLimitStorageFullException.class);
        limiter.check("one", false, null);
    }

    @Test
    void concurrentBusinessRejectionsNeverLeakGlobalPermits() throws Exception {
        var limiter = limiter(properties(10, 5));
        List<Integer> results = concurrent(100, i -> {
            try {
                limiter.check("one", false, "a");
                return 1;
            } catch (PublicRateLimitExceededException expected) {
                return 0;
            }
        });
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            limiter.check("one", false, "b");
        }
        assertThatThrownBy(() -> limiter.check("one", false, "c"))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }

    @Test
    void concurrentAdmissionKeepsTheRegistryStrictlyBounded() throws Exception {
        var p = properties(1, 1);
        p.setMaxBuckets(2);
        var limiter = limiter(p);
        List<Integer> results = concurrent(100, i -> {
            try {
                limiter.check("client-" + i, false, null);
                return 1;
            } catch (PublicRateLimitStorageFullException expected) {
                return 0;
            }
        });
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    }

    @Test
    void concurrentClientsKeepIndependentAllowances() throws Exception {
        var limiter = limiter(properties(10, 5));
        List<Integer> results = concurrent(100, i -> {
            try {
                limiter.check("client-" + (i % 2), false, "a");
                return 1;
            } catch (PublicRateLimitExceededException expected) {
                return 0;
            }
        });
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
    }

    @Test
    void restartResetsStateAndDisabledLimiterDoesNotTouchTheStore() {
        var p = properties(1, 1);
        var first = limiter(p);
        first.check("client", false, "a");
        assertThatThrownBy(() -> first.check("client", false, "a"))
                .isInstanceOf(PublicRateLimitExceededException.class);
        limiter(p).check("client", false, "a"); // Deliberate in-memory limitation.
        p.setEnabled(false);
        var store = org.mockito.Mockito.mock(PublicRateLimitBucketStore.class);
        new PublicRateLimiter(store, p).check("client", false, "a");
        org.mockito.Mockito.verifyNoInteractions(store);
    }

    @Test
    void expirationAndConcurrentRecreationCannotMultiplyAllowances() throws Exception {
        var p = properties(1, 1);
        p.setMaxBuckets(2);
        p.setIdleRetention(Duration.ofSeconds(1));
        var limiter = limiter(p);
        limiter.check("old", false, null);
        limiter.check("other-old", false, null);
        clock.advance(Duration.ofMinutes(1));
        List<Integer> results = concurrent(100, i -> {
            try {
                limiter.check(i % 2 == 0 ? "old" : "new", false, null);
                return 1;
            } catch (PublicRateLimitExceededException expected) {
                return 0;
            }
        });
        assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    }
    @Test
    void invalidBusinessConstructionDoesNotSpendExistingGlobalPermit() {
        var p = properties(2, 1);
        var limiter = limiter(p);
        limiter.check("one", false, null);
        p.getBusiness().getRead().setRefillTokens(2);
        p.getBusiness().getRead().setRefillPeriod(Duration.ofNanos(1));
        assertThatThrownBy(() -> limiter.check("one", false, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        limiter.check("one", false, null);
        assertThatThrownBy(() -> limiter.check("one", false, null))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }

    @Test
    void failedSecondConstructionDoesNotPublishFirstBucket() {
        var p = properties(1, 1);
        p.setMaxBuckets(2);
        p.getBusiness().getRead().setRefillTokens(2);
        p.getBusiness().getRead().setRefillPeriod(Duration.ofNanos(1));
        var limiter = limiter(p);
        assertThatThrownBy(() -> limiter.check("failed-client", false, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        // Both registry slots remain free after the failed request.
        limiter.check("other-one", false, null);
        limiter.check("other-two", false, null);
    }

    @Test
    void unexpectedFactoryFailureDoesNotSpendPermits() {
        var p = properties(2, 1);
        var limiter = limiter(p);
        limiter.check("one", false, null);
        var failing = org.mockito.Mockito.spy(limit(1));
        org.mockito.Mockito.doThrow(new IllegalStateException("Factory failure")).when(failing).toBandwidth();
        p.getBusiness().setRead(failing);
        assertThatThrownBy(() -> limiter.check("one", false, "a"))
                .isInstanceOf(IllegalStateException.class);
        limiter.check("one", false, null);
    }
    @Test
    void concurrentBusinessesCannotExceedSharedGlobalQuota() throws Exception {
        var limiter = limiter(properties(10, 6));
        List<Integer> results = concurrent(100, i -> {
            try {
                limiter.check("one", false, i % 2 == 0 ? "a" : "b");
                return i % 2 + 1;
            } catch (PublicRateLimitExceededException expected) {
                return 0;
            }
        });
        long a = results.stream().filter(value -> value == 1).count();
        long b = results.stream().filter(value -> value == 2).count();
        assertThat(a + b).isEqualTo(10);
        assertThat(a).isBetween(4L, 6L);
        assertThat(b).isBetween(4L, 6L);
    }

    @Test
    void concurrentReadWriteRequestsKeepSeparateAllowances() throws Exception {
        var limiter = limiter(properties(3, 2));
        List<Integer> results = concurrent(100, i -> {
            try {
                limiter.check("one", i % 2 != 0, "a");
                return i % 2 + 1;
            } catch (PublicRateLimitExceededException expected) {
                return 0;
            }
        });
        assertThat(results.stream().filter(value -> value == 1).count()).isEqualTo(2);
        assertThat(results.stream().filter(value -> value == 2).count()).isEqualTo(2);
    }

    @Test
    void defaultTenThousandEntryLimitIsEnforced() {
        var p = properties(1, 1);
        var limiter = limiter(p);
        for (int i = 0; i < 10_000; i++) {
            limiter.check("client-" + i, false, null);
        }
        assertThatThrownBy(() -> limiter.check("overflow", false, null))
                .isInstanceOf(PublicRateLimitStorageFullException.class);
        assertThatThrownBy(() -> limiter.check("client-0", false, null))
                .isInstanceOf(PublicRateLimitExceededException.class);
    }
    private List<Integer> concurrent(int count, java.util.function.IntFunction<Integer> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.apply(index);
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static class ManualClock implements TimeMeter {
        private final AtomicLong time = new AtomicLong();
        public long currentTimeNanos() { return time.get(); }
        public boolean isWallClockBased() { return false; }
        void advance(Duration duration) { time.addAndGet(duration.toNanos()); }
    }
}
