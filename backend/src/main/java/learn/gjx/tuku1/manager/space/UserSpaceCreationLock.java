package learn.gjx.tuku1.manager.space;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单 JVM 的按用户互斥锁，不是分布式锁。
 * 引用数包含持有者和等待者，最后一个调用结束才移除锁，避免等待期间出现第二把锁。
 */
@Component
public class UserSpaceCreationLock {
    private final ConcurrentHashMap<Long, Entry> locks = new ConcurrentHashMap<>();

    public <T> T execute(Long userId, Supplier<T> action) {
        Entry entry = locks.compute(userId, (key, existing) -> {
            Entry selected = existing == null ? new Entry() : existing;
            selected.references++;
            return selected;
        });
        try {
            synchronized (entry) {
                return action.get();
            }
        } finally {
            locks.compute(userId, (key, existing) -> {
                entry.references--;
                return entry.references == 0 ? null : entry;
            });
        }
    }

    // 包可见，仅供并发回归检查生命周期；不能用于业务同步判断。
    int retainedUserCount() {
        return locks.size();
    }

    private static final class Entry {
        // 只能在 ConcurrentHashMap.compute 内读写。
        private int references;
    }
}
