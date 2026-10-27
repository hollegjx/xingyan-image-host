package learn.gjx.tuku1.manager.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class LocalPicturePageCacheTest {
    @Test
    void capacityIsBoundedAndOversizedEntriesAreNotAdmitted() {
        PublicPictureCacheProperties properties = new PublicPictureCacheProperties();
        properties.setLocalMaxSize(3);
        properties.setLocalMaxEntryChars(8);
        LocalPicturePageCache cache = new LocalPicturePageCache(properties, () -> 0L);
        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, "json", false, Duration.ofMinutes(5), 0L);
        }
        // Caffeine maintenance may already be running asynchronously when cleanUp is called.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            cache.cleanUp();
            assertTrue(cache.size() <= 3);
        });
        cache.put("large", "123456789", false, Duration.ofMinutes(5), 0L);
        assertNull(cache.get("large"));
    }

    @Test
    void readsDoNotRenewTtlAndEmptyResultsExpireSooner() {
        AtomicLong time = new AtomicLong();
        LocalPicturePageCache cache = new LocalPicturePageCache(new PublicPictureCacheProperties(), time::get);
        cache.put("full", "json", false, Duration.ofMinutes(5), 0L);
        cache.put("empty", "json", true, Duration.ofMinutes(5), 0L);
        time.set(Duration.ofSeconds(9).toNanos());
        assertNotNull(cache.get("empty"));
        time.set(Duration.ofSeconds(11).toNanos());
        assertNull(cache.get("empty"));
        assertNotNull(cache.get("full"));
        time.set(Duration.ofSeconds(29).toNanos());
        assertNotNull(cache.get("full"));
        time.set(Duration.ofSeconds(31).toNanos());
        assertNull(cache.get("full"));
        assertTrue(cache.stats().hitCount() >= 3);
    }

    @Test
    void remoteRemainingTtlAndReadLatencyCapLocalLifetime() {
        AtomicLong time = new AtomicLong(Duration.ofSeconds(2).toNanos());
        LocalPicturePageCache cache = new LocalPicturePageCache(new PublicPictureCacheProperties(), time::get);
        cache.put("short", "json", false, Duration.ofSeconds(3), 0L);
        assertNotNull(cache.get("short"));
        time.set(Duration.ofSeconds(4).toNanos());
        assertNull(cache.get("short"));
        cache.put("expired", "json", false, Duration.ofSeconds(3), 0L);
        assertNull(cache.get("expired"));
    }

    @Test
    void disabledOrZeroCapacityLocalCacheDoesNotRetainData() {
        PublicPictureCacheProperties properties = new PublicPictureCacheProperties();
        properties.setLocalMaxSize(0);
        LocalPicturePageCache cache = new LocalPicturePageCache(properties, () -> 0L);
        cache.put("key", "json", false, Duration.ofMinutes(5), 0L);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            cache.cleanUp();
            assertEquals(0, cache.size());
        });
        properties.setLocalEnabled(false);
        assertNull(cache.get("key"));
    }

    @Test
    void invalidConfigurationFailsFast() {
        PublicPictureCacheProperties properties = new PublicPictureCacheProperties();
        properties.setLocalTtlSeconds(0);
        assertThrows(IllegalArgumentException.class, () -> new LocalPicturePageCache(properties, () -> 0L));
        properties.setLocalTtlSeconds(30);
        properties.setLocalMaxSize(-1);
        assertThrows(IllegalArgumentException.class, () -> new LocalPicturePageCache(properties, () -> 0L));
    }
}
