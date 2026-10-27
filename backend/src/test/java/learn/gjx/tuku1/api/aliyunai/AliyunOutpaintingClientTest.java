package learn.gjx.tuku1.api.aliyunai;

import com.fasterxml.jackson.databind.*;
import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.model.dto.picture.AiOutpaintingRequest;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class AliyunOutpaintingClientTest {
    private AiEditingProperties configured(){AiEditingProperties c=new AiEditingProperties();c.setApiKey("test-only");c.setWorkspaceId("ws-testspace");return c;}
    @Test void camelCaseDtoAndSnakeCaseProviderContract()throws Exception {
        ObjectMapper json=new ObjectMapper();AiOutpaintingRequest dto=json.readValue("{\"xScale\":1.5,\"yScale\":2}",AiOutpaintingRequest.class);
        assertEquals(1.5,dto.getXScale());
        AliyunOutpaintingClient client=new AliyunOutpaintingClient(configured(),json){
            protected JsonNode exchange(String url,byte[] body)throws IOException {
                assertEquals("https://ws-testspace.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",url);
                JsonNode payload=json.readTree(body);assertEquals(1,payload.path("parameters").path("n").asInt());
                assertEquals("1024*1024",payload.path("parameters").path("size").asText());
                assertFalse(payload.path("parameters").path("prompt_extend").asBoolean());
                assertFalse(payload.path("parameters").has("x_scale"));
                JsonNode message=payload.path("input").path("messages").get(0);
                assertEquals("user",message.path("role").asText());
                assertEquals("https://source.invalid/x",message.path("content").get(0).path("image").asText());
                assertTrue(message.path("content").get(1).path("text").asText().contains("1.50"));
                assertEquals("qwen-image-3.0",payload.path("model").asText());
                return json.readTree("{\"output\":{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":[{\"image\":\"https://result.invalid/a.png\"}]}}]}}");
            }
        };
        assertEquals("https://result.invalid/a.png",client.generate("https://source.invalid/x",dto.getXScale(),dto.getYScale(),1024,1024).url);
    }
    @Test void boundedProviderResponses(){assertThrows(IOException.class,()->AliyunOutpaintingClient.readLimited(new ByteArrayInputStream(new byte[100]),99));}
    @Test void workspaceCannotBecomeAnArbitraryHost(){AiEditingProperties c=configured();c.setWorkspaceId("localhost/secret");assertFalse(c.configured());assertThrows(IOException.class,()->new AliyunOutpaintingClient(c,new ObjectMapper()).generate("x",2,2,1024,1024));}
    @Test void outputBudgetAndDimensionMapping(){
        assertArrayEquals(new int[]{1024,1024},OutpaintingSize.calculate(600,600,2,2));
        int[] size=OutpaintingSize.calculate(960,720,1.25,1.25);assertTrue((long)size[0]*size[1]<=1_048_576);assertTrue(size[0]>960&&size[1]>720);
        assertThrows(IllegalArgumentException.class,()->OutpaintingSize.calculate(4096,512,3,1));
        assertThrows(IOException.class,()->new AliyunOutpaintingClient(configured(),new ObjectMapper()).generate("x",2,2,2048,2048));
    }
    @Test void malformedSuccessfulResponseCannotBeAccepted(){
        ObjectMapper json=new ObjectMapper();AliyunOutpaintingClient client=new AliyunOutpaintingClient(configured(),json){
            protected JsonNode exchange(String url,byte[] body)throws IOException{return json.readTree("{\"output\":{\"choices\":[]}}");}
        };
        assertThrows(IOException.class,()->client.generate("x",2,2,1024,1024));
    }
    @Test void optionalFinishReasonIsNotRequiredButOneImageAndRequestEvidenceArePreserved()throws Exception {
        ObjectMapper json=new ObjectMapper();AliyunOutpaintingClient client=new AliyunOutpaintingClient(configured(),json){
            protected JsonNode exchange(String url,byte[] body)throws IOException{return json.readTree("{\"request_id\":\"request-1\",\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://result.invalid/a.png\"}]}}]}}");}
        };
        AliyunOutpaintingClient.Generation result=client.generate("x",2,2,1024,1024);
        assertEquals("request-1",result.requestId);assertEquals("https://result.invalid/a.png",result.url);
    }
    @Test void realTransportDoesNotRetry503OrFollowRedirects()throws Exception {
        com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicInteger hits=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/fail",e->{hits.incrementAndGet();e.sendResponseHeaders(503,-1);e.close();});
        server.createContext("/redirect",e->{e.getResponseHeaders().add("Location","/fail");e.sendResponseHeaders(302,-1);e.close();});
        server.start();
        try {
            AliyunOutpaintingClient client=new AliyunOutpaintingClient(configured(),new ObjectMapper());
            String root="http://127.0.0.1:"+server.getAddress().getPort();
            assertThrows(AliyunOutpaintingClient.ProviderHttpException.class,()->client.exchange(root+"/fail",new byte[]{1}));assertEquals(1,hits.get());
            assertThrows(AliyunOutpaintingClient.ProviderHttpException.class,()->client.exchange(root+"/redirect",new byte[]{1}));assertEquals(1,hits.get());
        } finally {server.stop(0);}
    }
    @Test void capturesSafeIdentifiersNotProviderMessage() {
        AiEditingProperties config = new AiEditingProperties(); config.setApiKey("sk-test-secret");
        AliyunOutpaintingClient client = new AliyunOutpaintingClient(config, new ObjectMapper());
        AliyunOutpaintingClient.ProviderHttpException error = client.failure(403,
                "{\"code\":\"AccessDenied\",\"request_id\":\"req-123\",\"message\":\"sk-test-secret https://source.invalid/?signature=private\"}".getBytes(), null);
        assertEquals("AccessDenied", error.code); assertEquals("req-123", error.requestId);
        assertTrue(error.definitivelyRejected()); assertFalse(error.toString().contains("secret"));
        assertFalse(error.toString().contains("signature"));
    }
    @Test void malformedOrSecretBearingErrorsRemainSafe() {
        AiEditingProperties config = new AiEditingProperties(); config.setApiKey("sk-test-secret");
        AliyunOutpaintingClient client = new AliyunOutpaintingClient(config, new ObjectMapper());
        assertEquals("unavailable", client.failure(502, "<html>bad gateway</html>".getBytes(), "\nunsafe").code);
        AliyunOutpaintingClient.ProviderHttpException error = client.failure(401,
                "{\"error\":{\"code\":\"sk-test-secret\"},\"request_id\":\"https://source.invalid\"}".getBytes(), null);
        assertEquals("unavailable", error.code); assertEquals("unavailable", error.requestId);
    }
    @Test void uncertainHttpStatusesAreNotReportedAsRejections() {
        for (int status : new int[]{408,409,500,502,503,504})
            assertFalse(new AliyunOutpaintingClient.ProviderHttpException(status,"code","request").definitivelyRejected());
    }
}
