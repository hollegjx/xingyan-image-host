package learn.gjx.tuku1.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.entity.Picture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiLevelPicturePageCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private PictureListRevision revision;
    private PublicPictureCacheProperties properties;
    private final AtomicLong time = new AtomicLong();
    private PublicPicturePageCache cache;
    private PictureQueryRequest query;
    private ConcurrentMap<String, String> store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        revision = mock(PictureListRevision.class);
        properties = new PublicPictureCacheProperties();
        store = new ConcurrentHashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(revision.current()).thenReturn(1L);
        when(values.get(anyString())).thenAnswer(i -> store.get(i.getArgument(0)));
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(60000L);
        doAnswer(i -> { store.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        query = PublicPictureQuery.normalize(null);
        cache = newCache();
    }

    @Test
    void coldLoadPopulatesBothLevelsAndLocalHitSkipsRedis() {
        AtomicInteger loads = new AtomicInteger();
        Supplier<Page<Picture>> loader = () -> { loads.incrementAndGet(); return page(); };
        cache.get(query, loader);
        cache.get(query, loader);
        assertEquals(1, loads.get());
        verify(values, times(1)).get(anyString());
        verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
        verify(revision, times(2)).current();
    }

    @Test
    void privatePayloadInjectedIntoLocalCacheIsDiscarded() throws Exception {
        LocalPicturePageCache local = (LocalPicturePageCache)
                org.springframework.test.util.ReflectionTestUtils.getField(cache, "local");
        String payload = "{\"current\":1,\"size\":10,\"total\":1,\"records\":["
                + "{\"id\":999,\"spaceId\":101,\"reviewStatus\":1,\"uploadStatus\":1}]}";
        local.put(cache.key(query, 1), payload, false, Duration.ofSeconds(30), time.get());
        assertNull(cache.get(query, this::page).getRecords().get(0).getSpaceId());
        verify(values).get(anyString());
    }

    @Test
    void secondInstancePromotesRedisHitToItsOwnLocalCache() {
        cache.get(query, this::page);
        PublicPicturePageCache second = newCache();
        clearInvocations(values);
        second.get(query, noLoad());
        second.get(query, noLoad());
        verify(values, times(1)).get(anyString());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redis).getExpire(anyString(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void localExpirationFallsThroughToRedisWithoutRenewingRedisTtl() {
        cache.get(query, this::page);
        time.set(Duration.ofSeconds(31).toNanos());
        assertEquals(1, cache.get(query, noLoad()).getTotal());
        cache.get(query, noLoad());
        verify(values, times(2)).get(anyString());
        verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void promotionDoesNotExtendShortRemoteExpiry() {
        cache.get(query, this::page);
        PublicPicturePageCache second = newCache();
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(1000L);
        second.get(query, noLoad());
        store.clear(); // Redis entry expires.
        time.set(Duration.ofSeconds(2).toNanos());
        assertEquals(0, second.get(query, () -> new Page<>(1, 10, 0)).getTotal());
    }

    @Test
    void missingOrPersistentRedisTtlDoesNotPromoteToLocal() {
        cache.get(query, this::page);
        for (long ttl : new long[]{-2, -1, 0}) {
            PublicPicturePageCache second = newCache();
            when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(ttl);
            second.get(query, noLoad());
            clearInvocations(values);
            second.get(query, noLoad());
            verify(values).get(anyString());
        }
    }

    @Test
    void returnedMutableObjectsAreNeverSharedBetweenRequests() {
        Page<Picture> first = cache.get(query, this::page);
        first.getRecords().get(0).setName("first mutation");
        Page<Picture> second = cache.get(query, noLoad());
        assertEquals("original", second.getRecords().get(0).getName());
        second.getRecords().get(0).setName("second mutation");
        second.setTotal(500);
        Page<Picture> third = cache.get(query, noLoad());
        assertEquals("original", third.getRecords().get(0).getName());
        assertEquals(1, third.getTotal());
        assertNotSame(second.getRecords().get(0), third.getRecords().get(0));
    }

    @Test
    void remainingTtlFailureReturnsReadDataButDoesNotPromoteIt() {
        cache.get(query, this::page);
        PublicPicturePageCache second = newCache();
        when(redis.getExpire(anyString(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new IllegalStateException("PTTL unavailable"));
        assertEquals(1, second.get(query, noLoad()).getTotal());
        store.clear();
        time.set(Duration.ofSeconds(6).toNanos());
        assertEquals(0, second.get(query, () -> new Page<>(1, 10, 0)).getTotal());
    }

    @Test
    void oversizedPayloadKeepsRedisCachingButSkipsLocalAdmission() {
        properties.setLocalMaxEntryChars(1);
        cache.get(query, this::page);
        cache.get(query, noLoad());
        cache.get(query, noLoad());
        verify(values, times(3)).get(anyString());
        verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void revisionChangeInvalidatesBothLevelsAcrossTwoInstances() {
        PublicPicturePageCache second = newCache();
        cache.get(query, this::page);
        second.get(query, noLoad());
        when(revision.current()).thenReturn(2L);
        assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
        assertEquals(0, second.get(query, noLoad()).getTotal());
        assertEquals(2, store.size()); // Old generation remains physically stored, but is never selected.
    }

    @Test
    void revisionFailureMustNotServeEvenAWarmLocalCache() {
        cache.get(query, this::page);
        when(revision.current()).thenThrow(new IllegalStateException("primary database unavailable"));
        assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
        assertThrows(IllegalStateException.class,
                () -> cache.get(query, () -> { throw new IllegalStateException("database unavailable"); }));
    }

    @Test
    void redisFailureStillAllowsVerifiedLocalHitsAndFreshLocalFills() {
        cache.get(query, this::page);
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        PictureQueryRequest other = PublicPictureQuery.normalize(null);
        other.setCategory("different");
        cache.get(other, this::page); // Cold L1, failed Redis, successful DB.
        cache.get(other, noLoad()); // Newly populated L1 works.
        cache.get(query, noLoad()); // Existing L1 still works during Redis cooldown.
        when(revision.current()).thenReturn(2L);
        assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
        verify(values, times(2)).get(anyString());
    }

    @Test
    void redisWriteFailureDoesNotPreventLocalCachingAndRecoveryRetriesL2() {
        doThrow(new IllegalStateException("Redis OOM")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        cache.get(query, this::page);
        cache.get(query, noLoad());
        // After cooldown and L1 expiration, Redis is attempted again.
        time.set(Duration.ofSeconds(31).toNanos());
        cache.get(query, this::page);
        verify(values, times(2)).get(anyString());
    }

    @Test
    void switchesDeepPagesAndTransactionsBypassWarmLocalEntries() {
        cache.get(query, this::page);
        properties.setLocalEnabled(false);
        cache.get(query, noLoad()); // Redis-only mode.
        verify(values, times(2)).get(anyString());
        properties.setLocalEnabled(true);
        properties.setEnabled(false);
        assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
        properties.setEnabled(true);
        properties.setMaxCachedPage(0);
        assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
        properties.setMaxCachedPage(50);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void concurrentIdenticalMissesLoadDatabaseOnlyOnce() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        List<Future<Page<Picture>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(workers.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return cache.get(query, () -> { loads.incrementAndGet(); return page(); });
                }));
            }
            start.countDown();
            for (Future<Page<Picture>> future : futures) {
                assertEquals(1, future.get(5, TimeUnit.SECONDS).getTotal());
            }
            assertEquals(1, loads.get());
            verify(values, times(1)).get(anyString());
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void concurrentOldFillDoesNotPopulateNewVersionLocalCache() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        try {
            Future<Page<Picture>> old = worker.submit(() -> cache.get(query, () -> {
                loading.countDown();
                try {
                    assertTrue(resume.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return page();
            }));
            assertTrue(loading.await(5, TimeUnit.SECONDS));
            when(revision.current()).thenReturn(2L);
            resume.countDown();
            old.get(5, TimeUnit.SECONDS);
            assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
            assertEquals(0, cache.get(query, noLoad()).getTotal());
        } finally {
            resume.countDown();
            worker.shutdownNow();
        }
    }

    private PublicPicturePageCache newCache() {
        return new PublicPicturePageCache(redis, revision, properties, time::get);
    }

    private Supplier<Page<Picture>> noLoad() {
        return () -> { throw new AssertionError("should not query picture database"); };
    }

    private Page<Picture> page() {
        Picture picture = new Picture();
        picture.setId(10L);
        picture.setName("original");
        picture.setReviewStatus(1);
        picture.setUploadStatus(1);
        picture.setIsDelete(0);
        Page<Picture> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(picture));
        return page;
    }
}
