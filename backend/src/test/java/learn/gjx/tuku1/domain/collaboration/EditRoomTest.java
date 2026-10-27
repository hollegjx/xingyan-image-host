package learn.gjx.tuku1.domain.collaboration;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EditRoomTest {
    @Test void sameUserDifferentTabCannotStealOrRelease() {
        EditRoom r = new EditRoom("source"); r.enter("a", "1", "Alice", 0);
        assertThrows(IllegalStateException.class, () -> r.enter("b", "1", "Alice", 1));
        assertFalse(r.exit("b")); assertTrue(r.snapshot("a").editing);
        assertNull(r.snapshot("b").leaseToken); assertNotNull(r.snapshot("a").leaseToken);
    }
    @Test void onlyOneConcurrentWinner() throws Exception {
        EditRoom r = new EditRoom("source"); AtomicInteger winners = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8); CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] jobs = new Future[8];
            for (int i = 0; i < 8; i++) { final String id = "c" + i; jobs[i] = pool.submit(() -> {
                try { start.await(); r.enter(id, id, id, 0); winners.incrementAndGet(); }
                catch (IllegalStateException expected) { } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }); }
            start.countDown(); for (Future<?> job : jobs) job.get(3, TimeUnit.SECONDS);
            assertEquals(1, winners.get());
        } finally { pool.shutdownNow(); }
    }
    @Test void revisionsActionsAndBounds() {
        EditRoom r = new EditRoom("source"); r.enter("a", "1", "A", 0);
        r.action("a", 1, "ROTATE_LEFT", 1); assertEquals(3, r.snapshot("a").rotation);
        assertThrows(IllegalStateException.class, () -> r.action("a", 1, "ZOOM_IN", 2));
        r.action("a", 2, "ROTATE_RIGHT", 3); assertEquals(0, r.snapshot("a").rotation);
        for (long i = 3; i < 11; i++) r.action("a", i, "ZOOM_IN", 4);
        assertThrows(IllegalStateException.class, () -> r.action("a", 11, "ZOOM_IN", 5));
        for (long i = 11; i < 27; i++) r.action("a", i, "ZOOM_OUT", 6);
        assertEquals(-8, r.snapshot("a").zoom);
        assertThrows(IllegalStateException.class, () -> r.action("a", 27, "ZOOM_OUT", 7));
    }
    @Test void leaseAndHeartbeat() {
        EditRoom r = new EditRoom("s"); r.enter("a", "1", "A", 0); r.heartbeat("a", 40000);
        assertFalse(r.expire(45000)); assertTrue(r.expire(85000));
        assertFalse(r.snapshot("a").editing); r.enter("b", "2", "B", 85001);
    }
    @Test void saveCannotBypassOwnerVersionOrSource() {
        EditRoom r = new EditRoom("s"); r.enter("a", "1", "A", 0);
        String token = r.snapshot("a").leaseToken;
        assertThrows(IllegalStateException.class, () -> r.beginWrite("1", null, 1L, "s", 1));
        assertThrows(IllegalStateException.class, () -> r.beginWrite("2", token, 1L, "s", 1));
        assertThrows(IllegalStateException.class, () -> r.beginWrite("1", token, 0L, "s", 1));
        assertThrows(IllegalStateException.class, () -> r.beginWrite("1", token, 1L, "old", 1));
        r.beginWrite("1", token, 1L, "s", 1);
        assertThrows(IllegalStateException.class, () -> r.action("a", 1, "ZOOM_IN", 2));
        assertThrows(IllegalStateException.class, () -> r.enter("b", "2", "B", 90000));
        r.finishWrite("new"); assertEquals("new", r.snapshot("a").sourceVersion); assertFalse(r.snapshot("a").editing);
    }
    @Test void rollbackKeepsEditButDisconnectedOwnerIsReleased() {
        EditRoom r = new EditRoom("s"); r.enter("a", "1", "A", 0);
        r.beginWrite("1", r.snapshot("a").leaseToken, 1L, "s", 1); r.finishWrite(null);
        assertTrue(r.snapshot("a").editing);
        r.beginWrite("1", r.snapshot("a").leaseToken, 1L, "s", 2); r.exit("a"); r.finishWrite(null);
        assertFalse(r.snapshot("a").editing);
    }
    @Test void newSourceResetsPreviewAndLease() {
        EditRoom r = new EditRoom("s"); r.enter("a", "1", "A", 0); r.action("a", 1, "ZOOM_IN", 1);
        assertTrue(r.refreshSource("new")); assertEquals(0, r.snapshot("a").zoom); assertNull(r.snapshot("a").editorId);
    }
}
