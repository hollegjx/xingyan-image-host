package learn.gjx.tuku1.service;

import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.manager.*;
import learn.gjx.tuku1.manager.cache.PictureListRevision;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.PrivatePictureWriteManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.PrivatePictureLinkVO;
import learn.gjx.tuku1.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PicturePublicationIntegrationTest {
    static ConfigurableApplicationContext app;
    static JdbcTemplate jdbc;
    static final AtomicInteger sequence = new AtomicInteger();
    PictureServicePublication service;
    FileManager files;
    PrivatePictureFileManager privateFiles;
    PictureFileCleanupManager cleanup;
    @BeforeAll static void start() throws Exception {
        app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE).run(
            "--spring.config.location=optional:classpath:publication-empty.yml", "--spring.session.store-type=none",
            "--spring.datasource.url=jdbc:h2:mem:publication;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
            "--mybatis-plus.configuration.map-underscore-to-camel-case=false", "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete",
            "--logging.level.root=WARN");
        jdbc = app.getBean(JdbcTemplate.class);
        for (String file : Arrays.asList("sql/create_sql.sql", "sql/alter_public_file_cleanup.sql", "sql/alter_picture_submission.sql")) {
            String ddl = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8)
                .replaceFirst("(?i)use\\s+tuku\\s*;", "")
                .replaceAll("(?i)\\s+collate\\s*=?\\s*utf8mb4_(unicode_ci|bin)", "")
                .replaceAll("(?i)\\s+comment\\s*=?\\s*'[^']*'", "")
                .replaceAll("(?i)NOT NULL\\s+ON UPDATE CURRENT_TIMESTAMP", "ON UPDATE CURRENT_TIMESTAMP NOT NULL")
                .replace("ENGINE=InnoDB", "").replace("DEFAULT CHARSET=utf8mb4", "");
            new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(app.getBean(javax.sql.DataSource.class));
        }
    }
    @AfterAll static void stop() { if (app != null) app.close(); }
    @BeforeEach void resetData() {
        for (String table : Arrays.asList("picture_submission", "public_file_cleanup", "picture", "space_user", "space", "user")) jdbc.update("DELETE FROM " + table);
        for (long id=1;id<=5;id++) jdbc.update("INSERT INTO user(id,userName,userAccount,userPassword,userRole) VALUES(?,'fixture',?,?,?)", id,"fixture-"+id,"unused",id==5?1:0);
        jdbc.update("INSERT INTO space(id,spaceName,spaceType,userId,totalSize,totalCount) VALUES(10,'personal',0,1,123,1),(20,'team',1,1,123,1)");
        jdbc.update("INSERT INTO space_user(spaceId,userId,spaceRole) VALUES(20,1,'admin'),(20,2,'viewer'),(20,3,'editor'),(20,4,'admin')");
        for (long id : new long[]{10,20}) jdbc.update("INSERT INTO picture(id,spaceId,userId,uploadStatus,reviewStatus,name,introduction,category,tags,url,originalUrl,thumbnailUrl,picSize,picWidth,picHeight,picFormat) VALUES(?,?,1,1,1,'name','intro','category','[\"tag\"]',?,?,?,123,12,10,'jpg')",id,id,"private-display-"+id,"private-original-"+id,"private-thumbnail-"+id);
        service=app.getBean(PictureServicePublication.class); files=app.getBean(FileManager.class);
        cleanup=app.getBean(PictureFileCleanupManager.class); privateFiles=app.getBean(PrivatePictureFileManager.class);
        reset(files,privateFiles);
        when(files.copyPrivateForSubmission(any(),anyLong())).thenAnswer(i -> copy());
        when(privateFiles.temporaryDisplayLink(any(),anyInt())).thenReturn(new PrivatePictureLinkVO("opaque-test-value",new Date()));
    }
    User user(long id) { return app.getBean(UserMapper.class).selectById(id); }
    Picture picture(long id) { return app.getBean(PictureMapper.class).selectById(id); }
    Picture copy() {
        String prefix="https://public-test-1250000000.cos.ap-nanjing.myqcloud.com/public/1/"+sequence.incrementAndGet();
        Picture p=new Picture();p.setOriginalUrl(prefix+".jpg");p.setUrl(prefix+"_display.webp");p.setThumbnailUrl(prefix+"_thumbnail.webp");
        cleanup.registerUpload(PrivatePictureWriteManager.files(p)); return p;
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    @Test void personalOwnerCreatesSeparatePendingCopyAndAdoptsReceipts() {
        Picture before=picture(10); long id=service.submit(10L,user(1)); Picture result=picture(id);
        assertNotEquals(10L,id); assertNull(result.getSpaceId());assertEquals(0,result.getReviewStatus());assertEquals(1,result.getUploadStatus());
        assertNull(result.getSourceUrl());assertNull(result.getReviewerId());assertEquals(before.getName(),result.getName());
        assertEquals(before.getIntroduction(),result.getIntroduction());assertEquals(before.getCategory(),result.getCategory());assertEquals(before.getTags(),result.getTags());
        assertEquals(before,picture(10));assertEquals(0,count("public_file_cleanup"));assertEquals(1,count("picture_submission"));
        assertEquals(123,jdbc.queryForObject("SELECT totalSize FROM space WHERE id=10",Long.class));
    }
    @Test void teamAdminCanSubmitButSiteAdminCannotBypassMembership() {
        long id=service.submit(20L,user(4));assertEquals(4L,picture(id).getUserId());assertEquals(0,picture(id).getReviewStatus());
        assertThrows(BusinessException.class,()->service.submit(10L,user(5)));
        assertThrows(BusinessException.class,()->service.submit(20L,user(5)));
    }
    @ParameterizedTest @ValueSource(longs={2,3,5}) void viewerEditorAndOutsiderCannotPublishOrSign(long id) {
        assertThrows(BusinessException.class,()->service.submit(20L,user(id)));
        assertThrows(BusinessException.class,()->service.temporaryLink(20L,1,user(id)));
        verifyNoInteractions(files,privateFiles);
    }
    @Test void invalidPublicDraftAndDeletedSourcesNeverCopy() {
        assertThrows(BusinessException.class,()->service.submit(null,user(1)));
        assertThrows(BusinessException.class,()->service.submit(10L,null));
        for(String update:new String[]{"uploadStatus=0","uploadStatus=1,spaceId=NULL","spaceId=10,isDelete=1"}) {
            jdbc.update("UPDATE picture SET "+update+" WHERE id=10");assertThrows(BusinessException.class,()->service.submit(10L,user(1)));
        }
        verifyNoInteractions(files);
    }
    @Test void pendingOrApprovedCopyBlocksDuplicateRejectedCopyAllowsResubmission() {
        long first=service.submit(10L,user(1));assertThrows(BusinessException.class,()->service.submit(10L,user(1)));
        jdbc.update("UPDATE picture SET reviewStatus=1 WHERE id=?",first);assertThrows(BusinessException.class,()->service.submit(10L,user(1)));
        jdbc.update("UPDATE picture SET reviewStatus=2 WHERE id=?",first);assertNotEquals(first,service.submit(10L,user(1)));
        verify(files,times(2)).copyPrivateForSubmission(any(),anyLong());
    }
    @Test void concurrentSubmissionCommitsOnlyOneAndCompensatesLoser() throws Exception {
        CyclicBarrier gate=new CyclicBarrier(2);
        doAnswer(i->{Picture copied=copy();gate.await(5,TimeUnit.SECONDS);return copied;}).when(files).copyPrivateForSubmission(any(),anyLong());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> results=Arrays.asList(pool.submit(()->service.submit(10L,user(1))),pool.submit(()->service.submit(10L,user(1))));
            int success=0,denied=0;
            for(Future<Long> f:results)try{f.get(10,TimeUnit.SECONDS);success++;}catch(ExecutionException e){assertInstanceOf(BusinessException.class,e.getCause());denied++;}
            assertEquals(1,success);assertEquals(1,denied);assertEquals(1,count("picture_submission"));assertEquals(3,count("public_file_cleanup"));
        } finally { pool.shutdownNow(); }
    }
    @Test void revokedMemberDuringCopyCannotPublishAndReceiptsRemainForCleanup() {
        doAnswer(i->{Picture copied=copy();jdbc.update("DELETE FROM space_user WHERE spaceId=20 AND userId=4");return copied;}).when(files).copyPrivateForSubmission(any(),anyLong());
        assertThrows(BusinessException.class,()->service.submit(20L,user(4)));
        assertEquals(0,count("picture_submission"));assertEquals(3,count("public_file_cleanup"));
    }
    @Test void changedSourceDuringCopyCannotPublish() {
        doAnswer(i->{Picture copied=copy();jdbc.update("UPDATE picture SET url='new-reference' WHERE id=10");return copied;}).when(files).copyPrivateForSubmission(any(),anyLong());
        assertThrows(BusinessException.class,()->service.submit(10L,user(1)));assertEquals(0,count("picture_submission"));assertEquals(3,count("public_file_cleanup"));
    }
    @Test void databaseFailureRollsBackNewPictureAndRestoresIndependentReceipts() {
        doAnswer(i->{Picture copied=copy();jdbc.update("DELETE FROM picture_list_revision");return copied;}).when(files).copyPrivateForSubmission(any(),anyLong());
        try {assertThrows(BusinessException.class,()->service.submit(10L,user(1)));assertEquals(2,count("picture"));assertEquals(0,count("picture_submission"));assertEquals(3,count("public_file_cleanup"));}
        finally {jdbc.update("INSERT INTO picture_list_revision(id,revision) VALUES(1,0)");}
    }
    @ParameterizedTest @ValueSource(ints={1,24,168}) void allowedLifetimesOnlySignForAuthorizedPublisher(int hours) {
        assertNotNull(service.temporaryLink(10L,hours,user(1)));assertNotNull(service.temporaryLink(20L,hours,user(4)));
        assertEquals(0,count("picture_submission"));assertEquals(0,count("public_file_cleanup"));
    }
    @ParameterizedTest @ValueSource(ints={0,2,25,169,-1}) void otherLifetimesAreRejected(int hours) {
        assertThrows(BusinessException.class,()->service.temporaryLink(10L,hours,user(1)));verifyNoInteractions(privateFiles);
    }
    @TestConfiguration @EnableAutoConfiguration
    @Import({MybatisPlusConfig.class,PictureServicePublication.class,PictureAccessManager.class,PictureFileCleanupManager.class,PictureListRevision.class})
    static class TestApplication {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor externalStorage() {
            return f->{f.registerSingleton("fileManager",mock(FileManager.class));f.registerSingleton("privatePictureFileManager",mock(PrivatePictureFileManager.class));f.registerSingleton("storage",mock(CosManager.class));};
        }
        @Bean CosClientConfig config() { CosClientConfig c=mock(CosClientConfig.class);when(c.getBucket()).thenReturn("public-test-1250000000");when(c.getRegion()).thenReturn("ap-nanjing");return c; }
    }
}
