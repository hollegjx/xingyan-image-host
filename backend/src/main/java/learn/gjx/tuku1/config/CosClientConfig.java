package learn.gjx.tuku1.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.endpoint.SuffixEndpointBuilder;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 对象存储配置
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

    private String secretId;

    private String secretKey;

    private String region;

    private String bucket;

    /** Requires global acceleration to be enabled on the public bucket first. */
    private boolean accelerateEnabled = false;

    @Value("${picture.private-storage.bucket:}")
    private String privateStorageBucket = "";

    @Value("${picture.private-storage.accelerate-enabled:false}")
    private boolean privateStorageAccelerateEnabled = false;

    @Bean
    public COSClient cosClient() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(com.qcloud.cos.http.HttpProtocol.https);
        if (accelerateEnabled || privateStorageAccelerateEnabled) {
            if (accelerateEnabled && (bucket == null || bucket.trim().isEmpty())) {
                throw new IllegalArgumentException("COS public bucket is required for acceleration");
            }
            if (privateStorageAccelerateEnabled
                    && (privateStorageBucket == null || privateStorageBucket.trim().isEmpty())) {
                throw new IllegalArgumentException("COS private bucket is required for acceleration");
            }
            final String publicBucket = bucket;
            final String privateBucket = privateStorageBucket;
            final EndpointBuilder regional = clientConfig.getEndpointBuilder();
            final EndpointBuilder accelerated = new SuffixEndpointBuilder("cos.accelerate.myqcloud.com");
            // Each bucket requires its own explicit acceleration opt-in.
            clientConfig.setEndpointBuilder(new EndpointBuilder() {
                @Override
                public String buildGeneralApiEndpoint(String bucketName) {
                    return (accelerateEnabled && publicBucket.equals(bucketName))
                            || (privateStorageAccelerateEnabled && privateBucket.equals(bucketName))
                            ? accelerated.buildGeneralApiEndpoint(bucketName)
                            : regional.buildGeneralApiEndpoint(bucketName);
                }

                @Override
                public String buildGetServiceApiEndpoint() {
                    return regional.buildGetServiceApiEndpoint();
                }
            });
        }
        return new COSClient(cred, clientConfig);
    }
}
