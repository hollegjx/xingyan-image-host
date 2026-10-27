package learn.gjx.tuku1.infrastructure.collaboration;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.util.concurrent.ThreadFactory;

/** Bounded, non-spinning, single consumer. This is not a durable queue or cross-node lock. */
@Component
public class CollaborationQueue {
    public static class Event { Runnable task; }
    private final Disruptor<Event> disruptor;
    public CollaborationQueue() {
        ThreadFactory threads = r -> { Thread t = new Thread(r, "picture-collaboration"); t.setDaemon(true); return t; };
        disruptor = new Disruptor<>(Event::new, 256, threads, ProducerType.MULTI, new BlockingWaitStrategy());
        disruptor.handleEventsWith((event, sequence, end) -> {
            try { if (event.task != null) event.task.run(); } catch (RuntimeException ignored) {
                // Transport callbacks close their connection; never log messages, session IDs or edit tokens.
            } finally { event.task = null; }
        });
        disruptor.start();
    }
    public boolean offer(Runnable task) { return disruptor.getRingBuffer().tryPublishEvent((event, sequence) -> event.task = task); }
    @PreDestroy public void close() { disruptor.halt(); }
}
