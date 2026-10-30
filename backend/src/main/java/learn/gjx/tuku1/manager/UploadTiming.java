package learn.gjx.tuku1.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

/** 上传测量只接受固定阶段和数值，不能传入文件名、地址或异常。 */
public final class UploadTiming {
    private static final Logger LOG = LoggerFactory.getLogger(UploadTiming.class);

    public enum Stage { RECEIVE_LOCAL, RECEIVE_REMOTE, PREPARE_VARIANTS, COS_UPLOAD, PRIVATE_ACL, DATABASE }

    private UploadTiming() { }

    public static void record(Stage stage, long startedNanos, long bytes) {
        long elapsed = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        LOG.info("upload_timing stage={} elapsed_ms={} bytes={}", stage.name(), elapsed, Math.max(0, bytes));
    }
}
