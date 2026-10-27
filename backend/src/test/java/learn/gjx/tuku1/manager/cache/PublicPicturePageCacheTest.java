package learn.gjx.tuku1.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.entity.Picture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicPicturePageCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private PictureListRevision revision;
    private PublicPictureCacheProperties properties;
    private PublicPicturePageCache cache;
    private PictureQueryRequest query;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        revision = mock(PictureListRevision.class);
        properties = new PublicPictureCacheProperties();
        // These retain the Redis-only regression contract; L1 is tested separately.
        properties.setLocalEnabled(false);
        when(redis.opsForValue()).thenReturn(values);
        when(revision.current()).thenReturn(1L);
        cache = new PublicPicturePageCache(redis, revision, properties);
        query = PublicPictureQuery.normalize(null);
    }

    @Test
    void normalizedKeysAreStableAndCoverEveryQueryField() throws Exception {
        PictureQueryRequest first = new PictureQueryRequest();
        first.setTags(Arrays.asList("猫", "风景", "猫"));
        first.setSearchText("  日落 ");
        first.setReviewStatus(0);
        PictureQueryRequest second = new PictureQueryRequest();
        second.setTags(Arrays.asList("风景", "猫"));
        second.setSearchText("日落");
        second.setReviewStatus(2);
        second.setSortOrder("DESC");
        String key = cache.key(PublicPictureQuery.normalize(first), 1);
        assertEquals(key, cache.key(PublicPictureQuery.normalize(second), 1));
        assertNotEquals(key, cache.key(PublicPictureQuery.normalize(second), 2));
        // Reflect over the DTO so newly added filter fields cannot silently be omitted.
        for (java.lang.reflect.Field field : PictureQueryRequest.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || Arrays.asList("reviewStatus", "uploadStatus").contains(field.getName())) {
                continue;
            }
            PictureQueryRequest changed = PublicPictureQuery.normalize(null);
            field.setAccessible(true);
            Object value;
            if (field.getType() == String.class) {
                value = "sortField".equals(field.getName()) ? "id"
                        : "sortOrder".equals(field.getName()) ? "asc" : "different";
            } else if (field.getType() == Long.class) {
                value = 123L;
            } else if (field.getType() == Double.class) {
                value = 1.5;
            } else if (field.getType() == Integer.class) {
                value = 2;
            } else if (field.getType() == java.util.Date.class) {
                value = new java.util.Date(1767225600000L);
            } else {
                value = Collections.singletonList("猫");
            }
            field.set(changed, value);
            assertNotEquals(cache.key(query, 1), cache.key(changed, 1), field.getName());
        }
    }

    @Test
    void normalizationForcesPublicScopeWithoutMutatingCaller() {
        PictureQueryRequest source = new PictureQueryRequest();
        source.setReviewStatus(2);
        source.setUploadStatus(0);
        source.setCurrent(null);
        source.setPageSize(null);
        PictureQueryRequest result = PublicPictureQuery.normalize(source);
        assertEquals(1, result.getReviewStatus());
        assertEquals(1, result.getUploadStatus());
        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getPageSize());
        assertEquals(2, source.getReviewStatus());
    }

    @Test
    void dateRangesPartitionCacheAndEquivalentOffsetsShareAKey() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
        PictureQueryRequest first = json.readValue("{\"startEditTime\":\"2026-10-01T00:00:00.000+08:00\","
                + "\"endEditTime\":\"2026-10-02T00:00:00.000+08:00\"}", PictureQueryRequest.class);
        PictureQueryRequest equivalent = json.readValue("{\"startEditTime\":\"2026-09-30T16:00:00.000Z\","
                + "\"endEditTime\":\"2026-10-01T16:00:00.000Z\"}", PictureQueryRequest.class);
        assertEquals(cache.key(PublicPictureQuery.normalize(first), 1), cache.key(PublicPictureQuery.normalize(equivalent), 1));
        fakeStore();
        AtomicInteger calls = new AtomicInteger();
        Supplier<Page<Picture>> loader = () -> { calls.incrementAndGet(); return page(); };
        cache.get(PublicPictureQuery.normalize(first), loader);
        cache.get(PublicPictureQuery.normalize(equivalent), loader);
        assertEquals(1, calls.get());
        equivalent.setEndEditTime(new java.util.Date(equivalent.getEndEditTime().getTime() + 86400000L));
        cache.get(PublicPictureQuery.normalize(equivalent), loader);
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsInvalidPaginationAndUnsafeSortBeforeRedis() {
        query.setPageSize(0);
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalize(query));
        query.setPageSize(21);
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalize(query));
        query.setPageSize(10);
        query.setCurrent(-1);
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalize(query));
        query.setCurrent(1);
        query.setSortField("id desc; drop table picture");
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalize(query));
        verifyNoInteractions(redis);
    }

    @Test
    void cachesMissThenHitsWithoutRepeatingLoader() {
        ConcurrentMap<String, String> store = fakeStore();
        AtomicInteger calls = new AtomicInteger();
        Supplier<Page<Picture>> loader = () -> { calls.incrementAndGet(); return page(); };
        assertEquals(1, cache.get(query, loader).getRecords().size());
        assertEquals(1L, cache.get(query, loader).getRecords().get(0).getId());
        assertEquals(1, calls.get());
        assertEquals(1, store.size());
    }

    @Test
    void emptyPagesAreCachedAndTtlsHaveBoundedJitter() {
        fakeStore();
        AtomicInteger calls = new AtomicInteger();
        Supplier<Page<Picture>> loader = () -> { calls.incrementAndGet(); return new Page<>(1, 10, 0); };
        cache.get(query, loader);
        assertEquals(0, cache.get(query, loader).getTotal());
        assertEquals(1, calls.get());
        for (int i = 0; i < 100; i++) {
            assertTrue(cache.ttl(false).getSeconds() >= 300 && cache.ttl(false).getSeconds() <= 360);
            assertTrue(cache.ttl(true).getSeconds() >= 30 && cache.ttl(true).getSeconds() <= 40);
        }
    }

    @Test
    void readFailureFallsBackAndCooldownSkipsFurtherRedisCalls() {
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        assertEquals(1, cache.get(query, this::page).getTotal());
        assertEquals(1, cache.get(query, this::page).getTotal());
        verify(values, times(1)).get(anyString());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void writeFailureDoesNotFailSuccessfulDatabaseRead() {
        doThrow(new IllegalStateException("Redis read-only")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        assertEquals(1, cache.get(query, this::page).getTotal());
    }

    @Test
    void revisionReadFailureBypassesCacheButDatabaseErrorsStillPropagate() {
        when(revision.current()).thenThrow(new IllegalStateException("revision unavailable"));
        assertEquals(1, cache.get(query, this::page).getTotal());
        verifyNoInteractions(redis);
        assertThrows(IllegalArgumentException.class,
                () -> cache.get(query, () -> { throw new IllegalArgumentException("database failed"); }));
    }

    @Test
    void corruptOrNonPublicPayloadIsDiscarded() {
        when(values.get(anyString())).thenReturn("broken JSON",
                "{\"current\":1,\"size\":10,\"total\":1,\"records\":[{\"id\":1,\"reviewStatus\":2,\"uploadStatus\":1}]}");
        assertEquals(1, cache.get(query, this::page).getRecords().get(0).getReviewStatus());
        assertEquals(1, cache.get(query, this::page).getRecords().get(0).getReviewStatus());
        verify(redis, times(2)).delete(anyString());
    }

    @Test
    void disableDeepPagesAndActiveTransactionsBypassCache() {
        properties.setEnabled(false);
        cache.get(query, this::page);
        properties.setEnabled(true);
        query.setCurrent(51);
        cache.get(query, this::page);
        query.setCurrent(1);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.get(query, this::page);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(redis, revision);
    }

    @Test
    void concurrentOldFillCannotPoisonNewGeneration() throws Exception {
        fakeStore();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Page<Picture>> oldRead = executor.submit(() -> cache.get(query, () -> {
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
            oldRead.get(5, TimeUnit.SECONDS);
            // Simulates a committed delete/rejection: new generation must be empty.
            assertEquals(0, cache.get(query, () -> new Page<>(1, 10, 0)).getTotal());
            assertEquals(0, cache.get(query, () -> { throw new AssertionError("must hit v2"); }).getTotal());
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    private ConcurrentMap<String, String> fakeStore() {
        ConcurrentMap<String, String> store = new ConcurrentHashMap<>();
        when(values.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        return store;
    }

    @Test
    void privateQueriesAreRejectedEvenWhenCachingIsDisabled() {
        query.setSpaceId(101L);
        assertThrows(BusinessException.class, () -> PublicPictureQuery.normalize(query));
        properties.setEnabled(false);
        assertThrows(BusinessException.class, () -> cache.get(query, this::page));
        verifyNoInteractions(redis, revision);
    }

    @Test
    void privateRedisPayloadIsDiscardedAndNeverReturned() {
        when(values.get(anyString())).thenReturn("{\"current\":1,\"size\":10,\"total\":1,\"records\":["
                + "{\"id\":99,\"spaceId\":101,\"reviewStatus\":1,\"uploadStatus\":1}]}");
        assertEquals(1L, cache.get(query, this::page).getRecords().get(0).getId());
        verify(redis).delete(anyString());
    }

    @Test
    void privateDatabaseRowsFailClosedWithOrWithoutCache() {
        Page<Picture> privatePage = page();
        privatePage.getRecords().get(0).setSpaceId(101L);
        assertThrows(IllegalStateException.class, () -> cache.get(query, () -> privatePage));
        properties.setEnabled(false);
        assertThrows(IllegalStateException.class, () -> cache.get(query, () -> privatePage));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private Page<Picture> page() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setName("日落");
        picture.setUploadStatus(1);
        picture.setReviewStatus(1);
        picture.setIsDelete(0);
        Page<Picture> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(picture));
        return page;
    }
}
