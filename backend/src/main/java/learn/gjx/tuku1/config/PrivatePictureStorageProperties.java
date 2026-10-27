package learn.gjx.tuku1.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Disabled until an independently private COS bucket has been verified by the operator. */
@Data
@Component
@ConfigurationProperties(prefix = "picture.private-storage")
public class PrivatePictureStorageProperties {
    private boolean enabled;
    private String bucket;
}
