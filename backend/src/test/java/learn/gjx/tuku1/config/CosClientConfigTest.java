package learn.gjx.tuku1.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.http.HttpProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosClientConfigTest {
    private CosClientConfig config() {
        CosClientConfig config = new CosClientConfig();
        config.setSecretId("unit-test-id");
        config.setSecretKey("unit-test-key");
        config.setRegion("ap-nanjing");
        config.setBucket("public-test-1234567890");
        config.setPrivateStorageBucket("private-test-1234567890");
        return config;
    }

    @Test
    void defaultsToRegionalEndpoint() {
        CosClientConfig config = config();
        COSClient client = config.cosClient();
        try {
            assertFalse(config.isAccelerateEnabled());
            assertFalse(config.isPrivateStorageAccelerateEnabled());
            assertEquals("public-test-1234567890.cos.ap-nanjing.myqcloud.com",
                    client.getClientConfig().getEndpointBuilder().buildGeneralApiEndpoint(config.getBucket()));
            assertEquals(HttpProtocol.https, client.getClientConfig().getHttpProtocol());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void acceleratesOnlyPublicBucketAndPreservesRegionAndServiceEndpoint() {
        CosClientConfig config = config();
        config.setAccelerateEnabled(true);
        COSClient client = config.cosClient();
        try {
            EndpointBuilder endpoints = client.getClientConfig().getEndpointBuilder();
            assertEquals("public-test-1234567890.cos.accelerate.myqcloud.com",
                    endpoints.buildGeneralApiEndpoint(config.getBucket()));
            assertEquals("private-test-1234567890.cos.ap-nanjing.myqcloud.com",
                    endpoints.buildGeneralApiEndpoint("private-test-1234567890"));
            assertEquals("service.cos.myqcloud.com", endpoints.buildGetServiceApiEndpoint());
            assertEquals("ap-nanjing", config.getRegion());
            assertEquals("ap-nanjing", client.getClientConfig().getRegion().getRegionName());
            assertEquals(HttpProtocol.https, client.getClientConfig().getHttpProtocol());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void explicitlyAcceleratesPrivateBucketWithoutAffectingOtherBuckets() {
        for (boolean publicAcceleration : new boolean[]{false, true}) {
            CosClientConfig config = config();
            config.setAccelerateEnabled(publicAcceleration);
            config.setPrivateStorageAccelerateEnabled(true);
            COSClient client = config.cosClient();
            try {
                EndpointBuilder endpoints = client.getClientConfig().getEndpointBuilder();
                assertEquals("private-test-1234567890.cos.accelerate.myqcloud.com",
                        endpoints.buildGeneralApiEndpoint(config.getPrivateStorageBucket()));
                assertEquals("public-test-1234567890.cos."
                                + (publicAcceleration ? "accelerate" : "ap-nanjing") + ".myqcloud.com",
                        endpoints.buildGeneralApiEndpoint(config.getBucket()));
                assertEquals("other-test-1234567890.cos.ap-nanjing.myqcloud.com",
                        endpoints.buildGeneralApiEndpoint("other-test-1234567890"));
                assertEquals("service.cos.myqcloud.com", endpoints.buildGetServiceApiEndpoint());
                assertEquals("ap-nanjing", client.getClientConfig().getRegion().getRegionName());
                assertEquals(HttpProtocol.https, client.getClientConfig().getHttpProtocol());
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    void requiresPrivateBucketWhenPrivateAccelerationIsEnabled() {
        CosClientConfig config = config();
        config.setPrivateStorageAccelerateEnabled(true);
        config.setPrivateStorageBucket(" ");
        assertThrows(IllegalArgumentException.class, config::cosClient);
    }

    @Test
    void requiresPublicBucketWhenAccelerationIsEnabled() {
        CosClientConfig config = config();
        config.setAccelerateEnabled(true);
        config.setBucket(" ");
        assertThrows(IllegalArgumentException.class, config::cosClient);
    }
}
