package learn.gjx.tuku1.manager.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "picture.public-cache")
public class PublicPictureCacheProperties {
    private boolean enabled = true;
    /** Isolate different environments sharing a Redis database. */
    private String namespace = "tuku1:picture:public";
    private long ttlSeconds = 300;
    private long jitterSeconds = 60;
    private long emptyTtlSeconds = 30;
    private long failureCooldownSeconds = 5;
    private int maxCachedPage = 50;
    private boolean localEnabled = true;
    private long localMaxSize = 1000;
    private long localTtlSeconds = 30;
    private long localEmptyTtlSeconds = 10;
    /** Bound each JSON string as well as entry count (UTF-16 characters, not exact heap bytes). */
    private int localMaxEntryChars = 32768;
}
