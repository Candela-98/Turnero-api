package com.turnero.api.config;

import io.github.bucket4j.Bandwidth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "public-booking.rate-limit")
public class PublicRateLimitProperties {
    private boolean enabled;
    @NotNull @Valid
    private LimitConfig read;
    @NotNull @Valid
    private LimitConfig write;
    @NotNull @Valid
    private BusinessLimits business;
    @Min(2)
    private int maxBuckets = 10_000;
    @NotNull
    private Duration idleRetention = Duration.ofMinutes(10);

    @AssertTrue(message = "idleRetention must be positive and fit in nanoseconds")
    public boolean isIdleRetentionValid() {
        return positiveNanos(idleRetention);
    }

    private static boolean positiveNanos(Duration value) {
        try {
            return value != null && value.toNanos() > 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    public static class BusinessLimits {
        @NotNull @Valid
        private LimitConfig read;
        @NotNull @Valid
        private LimitConfig write;

        public LimitConfig getRead() { return read; }
        public void setRead(LimitConfig read) { this.read = read; }
        public LimitConfig getWrite() { return write; }
        public void setWrite(LimitConfig write) { this.write = write; }
    }

    public static class LimitConfig {
        @Positive
        private int capacity;
        @Positive
        private int refillTokens;
        @NotNull
        private Duration refillPeriod;

        @AssertTrue(message = "refillPeriod must be positive and fit in nanoseconds")
        public boolean isRefillPeriodPositive() {
            return positiveNanos(refillPeriod);
        }

        @AssertTrue(message = "rate limit configuration must be supported by Bucket4j")
        public boolean isBucket4jCompatible() {
            if (capacity <= 0 || refillTokens <= 0 || !positiveNanos(refillPeriod)) {
                return false;
            }
            try {
                toBandwidth();
                return true;
            } catch (IllegalArgumentException | ArithmeticException exception) {
                return false;
            }
        }

        // Share the same construction rules between startup validation and storage.
        public Bandwidth toBandwidth() {
            return Bandwidth.builder().capacity(capacity)
                    .refillGreedy(refillTokens, refillPeriod).build();
        }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        public Duration getRefillPeriod() { return refillPeriod; }
        public void setRefillPeriod(Duration refillPeriod) { this.refillPeriod = refillPeriod; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LimitConfig getRead() { return read; }
    public void setRead(LimitConfig read) { this.read = read; }
    public LimitConfig getWrite() { return write; }
    public void setWrite(LimitConfig write) { this.write = write; }
    public BusinessLimits getBusiness() { return business; }
    public void setBusiness(BusinessLimits business) { this.business = business; }
    public int getMaxBuckets() { return maxBuckets; }
    public void setMaxBuckets(int maxBuckets) { this.maxBuckets = maxBuckets; }
    public Duration getIdleRetention() { return idleRetention; }
    public void setIdleRetention(Duration idleRetention) { this.idleRetention = idleRetention; }
}
