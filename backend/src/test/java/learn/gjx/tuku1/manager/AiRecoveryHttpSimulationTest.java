package learn.gjx.tuku1.manager;

import com.fasterxml.jackson.databind.*;
import com.qcloud.cos.COSClient;
import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.dto.picture.AiOutpaintingRequest;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real worker/service/database/HTTP client; only COS signing and identity use substitutes. */
@EnabledIfEnvironmentVariable(named="AI_SIMULATION_URL",matches="http://127\\.0\\.0\\.1:[0-9]+")
class AiRecoveryHttpSimulationTest {
    private final ObjectMapper json=new ObjectMapper();
    private final String base=System.getenv("AI_SIMULATION_URL");

    @Test void uncertainResponsesAndReceiptRecoveryNeverResubmitGeneration() throws Exception {
        for(String scenario:Arrays.asList("error-json","error-html","drop","rejected","receipt-error","receipt")) {
            local("/reset","POST");
            DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:sim_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            String sql=new String(Files.readAllBytes(Paths.get("sql/alter_ai_picture_task.sql")),StandardCharsets.UTF_8).replace("ENGINE=InnoDB","").replace("DEFAULT CHARSET=utf8mb4","");
            new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(ds);
            AiEditingProperties config=new AiEditingProperties();config.setEnabled(true);config.setApiKey("test-only");config.setWorkspaceId("ws-testspace");config.getAllowedUserIds().add(1L);
            AiTaskStore store=new AiTaskStore(new JdbcTemplate(ds),new TransactionTemplate(new DataSourceTransactionManager(ds)),config);
            COSClient cos=mock(COSClient.class);CosClientConfig cosConfig=new CosClientConfig();cosConfig.setBucket("test");cosConfig.setRegion("ap-test");
            when(cos.generatePresignedUrl(anyString(),anyString(),any(Date.class),any(com.qcloud.cos.http.HttpMethodName.class))).thenReturn(new URL("https://source.fixture.test/input.png"));
            AiEditingFiles files=new AiEditingFiles(cos,cosConfig,mock(PrivatePictureFileManager.class),config) {
                @Override protected HttpURLConnection openResultConnection(String validatedUrl)throws IOException {
                    return (HttpURLConnection)new URL(base+"/provider"+URI.create(validatedUrl).getPath()).openConnection();
                }
            };
            AliyunOutpaintingClient provider=new AliyunOutpaintingClient(config,json) {
                @Override protected JsonNode exchange(String url,byte[] body)throws IOException {
                    return super.exchange(base+"/provider"+URI.create(url).getPath()+"?case="+scenario,body);
                }
            };
            User user=new User();user.setId(1L);user.setUserRole(0);
            Picture picture=new Picture();picture.setId(11L);picture.setUrl("https://test.cos.ap-test.myqcloud.com/public/1/source.jpg");picture.setPicWidth(600);picture.setPicHeight(600);picture.setPicFormat("png");
            PictureMapper pictures=mock(PictureMapper.class);when(pictures.selectById(11L)).thenReturn(picture);
            PictureService uploads=mock(PictureService.class);
            AiPictureService service=new AiPictureService(pictures,mock(PictureAccessManager.class),uploads,store,files,config);
            UserMapper users=mock(UserMapper.class);when(users.selectById(1L)).thenReturn(user);
            AiGenerationWorker worker=new AiGenerationWorker(store,service,users,files,provider,config);
            try {
                AiOutpaintingRequest input=new AiOutpaintingRequest();input.setPictureId(11L);input.setRequestId(UUID.randomUUID().toString());input.setXScale(2d);input.setYScale(2d);input.setExternalProcessingConsent(true);
                String id=service.create(input,user,"/api").getId();worker.runOne();worker.runOne();
                String expected="rejected".equals(scenario)?"FAILED":"UNKNOWN";
                assertEquals(expected,service.get(id,user,"/api").getStatus(),scenario);
                config.getResultHosts().add("result.fixture.test");
                if("receipt".equals(scenario)) {
                    assertEquals("SUCCEEDED",service.recover(id,user,"/api").getStatus());
                    assertNotNull(service.get(id,user,"/api").getResultUrl());
                } else {
                    assertThrows(BusinessException.class,()->service.recover(id,user,"/api"),scenario);
                    assertEquals(expected,store.find(id,1L).getStatus());
                }
                worker.runOne();assertEquals(1,local("/stats","GET").path("calls").asInt(),scenario);
                verifyNoInteractions(uploads);
            } finally {worker.close();}
        }
    }

    private JsonNode local(String path,String method)throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(base+"/provider"+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(2000);c.setReadTimeout(5000);
        try(InputStream in=c.getInputStream()){return json.readTree(in);}finally{c.disconnect();}
    }
}
