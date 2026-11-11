package com.turnero.api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.turnero.api.config.PublicRateLimitProperties;
import com.turnero.api.exception.PublicRateLimitStorageFullException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-instance registry. No size/time eviction: only full, inactive buckets can
 * be discarded. All access stays inside this monitor, including both-scope consumption.
 */
@Component
public class PublicRateLimitBucketStore {
    private final Cache<Key, Entry> buckets = Caffeine.newBuilder().build();
    private final int maxBuckets;
    private final long idleNanos;
    private final TimeMeter clock;
    private long lastCleanup;

    @Autowired
    public PublicRateLimitBucketStore(PublicRateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_NANOTIME);
    }

    PublicRateLimitBucketStore(PublicRateLimitProperties properties, TimeMeter clock) {
        this.maxBuckets = properties.getMaxBuckets();
        this.idleNanos = properties.getIdleRetention().toNanos();
        this.clock = clock;
        this.lastCleanup = clock.currentTimeNanos();
    }

    // Returns zero on admission, otherwise the time until both scopes can admit.
    // No Bucket reference escapes: admission, cleanup and consumption share one lock.
    synchronized long tryConsume(String ip, boolean write, String businessScope,
                                 PublicRateLimitProperties.LimitConfig globalConfig,
                                 PublicRateLimitProperties.LimitConfig businessConfig) {
        long now = clock.currentTimeNanos();
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec(new Key(ip, write, null), globalConfig));
        if (businessScope != null) {
            specs.add(new Spec(new Key(ip, write, businessScope), businessConfig));
        }

        long wait = 0;
        int missing = 0;
        for (Spec spec : specs) {
            Entry entry = buckets.getIfPresent(spec.key());
            if (entry == null) {
                missing++;
            } else {
                entry.lastAccess = now;
                wait = Math.max(wait, entry.bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill());
            }
        }
        if (wait > 0) {
            return wait;
        }

        if (missing > 0) {
            // Bound cleanup CPU under a flood of distinct keys.
            if (now - lastCleanup >= 1_000_000_000L) {
                buckets.asMap().entrySet().removeIf(item -> {
                    Entry entry = item.getValue();
                    return now - entry.lastAccess >= idleNanos
                            && entry.bucket.getAvailableTokens() == entry.capacity;
                });
                lastCleanup = now;
            }
            if (buckets.asMap().size() + missing > maxBuckets) {
                throw new PublicRateLimitStorageFullException();
            }
        }

        // Prepare ALL missing buckets before publishing any entry or spending a permit.
        // Startup validation is the first defense; this also protects direct callers
        // if a factory/configuration fails while preparing the second scope.
        List<Entry> ready = new ArrayList<>();
        for (Spec spec : specs) {
            Entry entry = buckets.getIfPresent(spec.key());
            if (entry == null) {
                Bucket bucket = Bucket.builder()
                        .withCustomTimePrecision(clock)
                        .addLimit(spec.config().toBandwidth())
                        .build();
                entry = new Entry(bucket, spec.config().getCapacity(), now);
            }
            ready.add(entry);
        }

        for (int i = 0; i < specs.size(); i++) {
            buckets.put(specs.get(i).key(), ready.get(i));
        }
        for (Entry entry : ready) {
            // No competing consumer or eviction can run between estimate and consume.
            if (!entry.bucket.tryConsume(1)) {
                throw new IllegalStateException("Rate limit admission invariant violated");
            }
            entry.lastAccess = now;
        }
        return 0;
    }

    private record Key(String ip, boolean write, String businessScope) {}
    private record Spec(Key key, PublicRateLimitProperties.LimitConfig config) {}

    private static final class Entry {
        private final Bucket bucket;
        private final long capacity;
        private long lastAccess;

        private Entry(Bucket bucket, long capacity, long lastAccess) {
            this.bucket = bucket;
            this.capacity = capacity;
            this.lastAccess = lastAccess;
        }
    }
}
