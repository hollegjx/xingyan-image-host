package learn.gjx.tuku1.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** The tutorial uses an unofficial web endpoint; enable only after a real acceptance check. */
@Data
@Component
@ConfigurationProperties(prefix = "picture.image-search")
public class ImageSearchProperties {
    private boolean enabled = false;
}
