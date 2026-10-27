package learn.gjx.tuku1.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache-aside for public picture rows only. User enrichment remains outside this cache.
 * Never cache under a newly read revision after loading: a concurrent writer may have committed.
 */
@Slf4j
@Component
public class PublicPicturePageCache {
    private final StringRedisTemplate redis;
    private final PictureListRevision revision;
    private final PublicPictureCacheProperties properties;
    private final LocalPicturePageCache local;
    private final Ticker ticker;
    private final ObjectMapper json = new ObjectMapper().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    private final Object[] loadLocks = new Object[64];
    private volatile long retryAfterNanos;

    @Autowired
    public PublicPicturePageCache(StringRedisTemplate redis, PictureListRevision revision,
                                  PublicPictureCacheProperties properties) {
        this(redis, revision, properties, Ticker.systemTicker());
    }

    PublicPicturePageCache(StringRedisTemplate redis, PictureListRevision revision,
                           PublicPictureCacheProperties properties, Ticker ticker) {
        this.redis = redis;
        this.revision = revision;
        this.properties = properties;
        this.ticker = ticker;
        this.local = new LocalPicturePageCache(properties, ticker);
        for (int i = 0; i < loadLocks.length; i++) {
            loadLocks[i] = new Object();
        }
    }

    public Page<Picture> get(PictureQueryRequest query, Supplier<Page<Picture>> loader) {
        if (query.getSpaceId() != null) {
            throw new learn.gjx.tuku1.exception.BusinessException(
                    learn.gjx.tuku1.exception.ErrorCode.PARAMS_ERROR, "私有图片不能进入公共缓存");
        }
        if (!properties.isEnabled() || query.getCurrent() > properties.getMaxCachedPage()
                || TransactionSynchronizationManager.isActualTransactionActive()) {
            return loadPublic(loader);
        }
        final String key;
        try {
            key = key(query, revision.current());
        } catch (Exception e) {
            failOpen(e);
            // Never serve L1/L2 when the authoritative revision cannot be verified.
            return loadPublic(loader);
        }
        Page<Picture> localHit = readLocal(key, query);
        if (localHit != null) {
            return localHit;
        }
        // Fixed-size striped locks bound memory and coalesce identical misses within this process.
        synchronized (loadLocks[(key.hashCode() & Integer.MAX_VALUE) % loadLocks.length]) {
            localHit = readLocal(key, query);
            if (localHit != null) {
                return localHit;
            }
            if (!coolingDown()) {
                try {
                    long readStarted = ticker.read();
                    String value = redis.opsForValue().get(key);
                    if (value != null) {
                        CachedPage cached;
                        try {
                            cached = decode(value, query);
                        } catch (Exception corrupt) {
                            log.warn("公开图片缓存数据损坏，丢弃并回源，key={}", key);
                            redis.delete(key);
                            cached = null;
                        }
                        if (cached != null) {
                            promoteToLocal(key, value, cached.getRecords().isEmpty(), readStarted);
                            return cached.toPage();
                        }
                    }
                } catch (Exception e) {
                    failOpen(e);
                }
            }
            // Database/loader errors are not cache errors: do not swallow or cache them.
            Page<Picture> page = loadPublic(loader);
            try {
                CachedPage cached = CachedPage.from(page);
                if (valid(cached, query)) {
                    String value = json.writeValueAsString(cached);
                    Duration lifetime = ttl(page.getRecords().isEmpty());
                    long writeStarted = ticker.read();
                    if (!coolingDown()) {
                        try {
                            redis.opsForValue().set(key, value, lifetime);
                        } catch (Exception e) {
                            failOpen(e);
                        }
                    }
                    // Fresh DB data can still populate L1 when Redis is unavailable.
                    local.put(key, value, page.getRecords().isEmpty(), lifetime, writeStarted);
                }
            } catch (Exception e) {
                failOpen(e);
            }
            return page;
        }
    }

    private Page<Picture> readLocal(String key, PictureQueryRequest query) {
        String value = local.get(key);
        if (value == null) {
            return null;
        }
        try {
            // Each caller gets new mutable objects, not references held by another request.
            return decode(value, query).toPage();
        } catch (Exception e) {
            local.invalidate(key);
            log.warn("本地图片缓存数据损坏，丢弃并继续查询，key={}", key);
            return null;
        }
    }

    private Page<Picture> loadPublic(Supplier<Page<Picture>> loader) {
        Page<Picture> page = loader.get();
        if (page.getRecords().stream().anyMatch(p -> p != null && p.getSpaceId() != null)) {
            throw new IllegalStateException("Private rows must never be returned by a public loader");
        }
        return page;
    }

    private CachedPage decode(String value, PictureQueryRequest query) throws Exception {
        CachedPage cached = json.readValue(value, CachedPage.class);
        if (!valid(cached, query)) {
            throw new IllegalArgumentException("Invalid cached public page");
        }
        return cached;
    }

    private void promoteToLocal(String key, String value, boolean empty, long readStarted) {
        if (properties.isLocalEnabled()) {
            try {
                Long ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
                // Missing / persistent / expired keys must not receive a fresh L1 lifetime.
                if (ttlMillis != null && ttlMillis > 0) {
                    local.put(key, value, empty, Duration.ofMillis(ttlMillis), readStarted);
                }
            } catch (Exception e) {
                // Already-read valid L2 data may satisfy this request; don't extend it into L1.
                failOpen(e);
            }
        }
    }

    String key(PictureQueryRequest query, long version) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsString(query).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(64);
        for (byte b : hash) {
            hex.append(String.format("%02x", b & 0xff));
        }
        // Discard pre-isolation cached totals as well as records after deployment.
        return properties.getNamespace() + ":v2:" + version + ":" + hex;
    }

    Duration ttl(boolean empty) {
        long base = Math.max(1, Math.min(86400, empty ? properties.getEmptyTtlSeconds() : properties.getTtlSeconds()));
        long jitter = Math.max(0, Math.min(3600, properties.getJitterSeconds()));
        if (empty) {
            jitter = Math.min(jitter, 10);
        }
        return Duration.ofSeconds(base + ThreadLocalRandom.current().nextLong(jitter + 1));
    }

    private boolean valid(CachedPage page, PictureQueryRequest query) {
        return page != null && page.getCurrent() == query.getCurrent() && page.getSize() == query.getPageSize()
                && page.getTotal() >= 0 && page.getRecords() != null
                && page.getRecords().size() <= page.getSize() && page.getTotal() >= page.getRecords().size()
                && page.getRecords().stream().allMatch(p -> p != null && p.getId() != null
                && p.getSpaceId() == null
                && Objects.equals(p.getUploadStatus(), PictureUploadStatusEnum.COMPLETED.getValue())
                && Objects.equals(p.getReviewStatus(), PictureReviewStatusEnum.PASS.getValue())
                && !Objects.equals(p.getIsDelete(), 1));
    }

    private boolean coolingDown() {
        return retryAfterNanos != 0 && ticker.read() - retryAfterNanos < 0;
    }

    private synchronized void failOpen(Exception e) {
        if (!coolingDown()) {
            log.warn("公开图片缓存发生故障，Redis 暂停尝试；仅使用已核验版本的本地缓存或回源（不降级 Session）：{}", e.toString());
        }
        retryAfterNanos = ticker.read()
                + Duration.ofSeconds(Math.max(1, Math.min(60, properties.getFailureCooldownSeconds()))).toNanos();
    }

    @Data
    public static class CachedPage {
        private long current;
        private long size;
        private long total;
        private List<Picture> records;

        static CachedPage from(Page<Picture> page) {
            CachedPage cached = new CachedPage();
            cached.setCurrent(page.getCurrent());
            cached.setSize(page.getSize());
            cached.setTotal(page.getTotal());
            cached.setRecords(page.getRecords());
            return cached;
        }

        Page<Picture> toPage() {
            Page<Picture> page = new Page<>(current, size, total);
            page.setRecords(records);
            return page;
        }
    }
}
