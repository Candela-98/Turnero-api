package com.turnero.api.ratelimit;

import com.turnero.api.config.PublicRateLimitProperties;
import com.turnero.api.exception.PublicRateLimitExceededException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class PublicRateLimiter {
    private final PublicRateLimitBucketStore bucketStore;
    private final PublicRateLimitProperties properties;

    public PublicRateLimiter(PublicRateLimitBucketStore bucketStore, PublicRateLimitProperties properties) {
        this.bucketStore = bucketStore;
        this.properties = properties;
    }

    public void check(String clientIp, boolean write, String businessSlug) {
        if (!properties.isEnabled()) {
            return;
        }
        var global = write ? properties.getWrite() : properties.getRead();
        var business = write ? properties.getBusiness().getWrite() : properties.getBusiness().getRead();
        long nanos = bucketStore.tryConsume(clientIp, write,
                businessSlug == null ? null : businessScope(businessSlug), global, business);
        if (nanos > 0) {
            long seconds = nanos / 1_000_000_000L + (nanos % 1_000_000_000L == 0 ? 0 : 1);
            throw new PublicRateLimitExceededException(Math.max(1, seconds));
        }
    }

    // Fixed-size INTERNAL business identifier bounds key memory. Never used for tokens,
    // logs or metric labels. Preserve slug case to match BusinessRepository.findBySlug.
    private String businessScope(String slug) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(slug.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
