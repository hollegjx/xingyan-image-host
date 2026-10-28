package learn.gjx.tuku1.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.endpoint.SuffixEndpointBuilder;
import com.qcloud.cos.region.Region;
import lombok.Data;
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

    @Bean
    public COSClient cosClient() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(com.qcloud.cos.http.HttpProtocol.https);
        if (accelerateEnabled) {
            if (bucket == null || bucket.trim().isEmpty()) {
                throw new IllegalArgumentException("COS public bucket is required for acceleration");
            }
            final String publicBucket = bucket;
            final EndpointBuilder regional = clientConfig.getEndpointBuilder();
            final EndpointBuilder accelerated = new SuffixEndpointBuilder("cos.accelerate.myqcloud.com");
            // The shared client also serves private storage. Never route another bucket
            // through the public bucket's endpoint or opt it into acceleration implicitly.
            clientConfig.setEndpointBuilder(new EndpointBuilder() {
                @Override
                public String buildGeneralApiEndpoint(String bucketName) {
                    return publicBucket.equals(bucketName)
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
