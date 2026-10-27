package learn.gjx.tuku1.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;

/**
 * Process-local L1. Store immutable JSON, never share mutable Page/Picture instances.
 * Per-entry expiry is capped by the L2 remaining TTL; reads never extend it.
 */
final class LocalPicturePageCache {
    private final Cache<String, Entry> cache;
    private final PublicPictureCacheProperties properties;
    private final Ticker ticker;

    LocalPicturePageCache(PublicPictureCacheProperties properties, Ticker ticker) {
        if (properties.getLocalMaxSize() < 0 || properties.getLocalMaxEntryChars() <= 0
                || properties.getLocalTtlSeconds() <= 0 || properties.getLocalTtlSeconds() > 3600
                || properties.getLocalEmptyTtlSeconds() <= 0 || properties.getLocalEmptyTtlSeconds() > 3600) {
            throw new IllegalArgumentException("本地缓存容量不能为负，单条长度必须为正，TTL 必须在 1~3600 秒内");
        }
        this.properties = properties;
        this.ticker = ticker;
        cache = Caffeine.newBuilder().maximumSize(properties.getLocalMaxSize()).ticker(ticker)
                .recordStats().expireAfter(new Expiry<String, Entry>() {
                    @Override
                    public long expireAfterCreate(String key, Entry value, long currentTime) {
                        return value.ttlNanos;
                    }

                    @Override
                    public long expireAfterUpdate(String key, Entry value, long currentTime, long currentDuration) {
                        return value.ttlNanos;
                    }

                    @Override
                    public long expireAfterRead(String key, Entry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                }).build();
    }

    String get(String key) {
        if (!properties.isLocalEnabled()) {
            return null;
        }
        Entry entry = cache.getIfPresent(key);
        return entry == null ? null : entry.value;
    }

    void put(String key, String value, boolean empty, Duration remainingTtl, long observedAtNanos) {
        if (!properties.isLocalEnabled() || value.length() > properties.getLocalMaxEntryChars()) {
            return;
        }
        long elapsed = Math.max(0, ticker.read() - observedAtNanos);
        long ttl = Math.min(Duration.ofSeconds(empty
                ? properties.getLocalEmptyTtlSeconds() : properties.getLocalTtlSeconds()).toNanos(),
                remainingTtl.toNanos() - elapsed);
        if (ttl > 0) {
            cache.put(key, new Entry(value, ttl));
        }
    }

    void invalidate(String key) {
        cache.invalidate(key);
    }

    // Package-level diagnostics and deterministic capacity tests; not an unauthenticated HTTP API.
    void cleanUp() {
        cache.cleanUp();
    }

    long size() {
        return cache.estimatedSize();
    }

    CacheStats stats() {
        return cache.stats();
    }

    private static final class Entry {
        private final String value;
        private final long ttlNanos;

        private Entry(String value, long ttlNanos) {
            this.value = value;
            this.ttlNanos = ttlNanos;
        }
    }
}
