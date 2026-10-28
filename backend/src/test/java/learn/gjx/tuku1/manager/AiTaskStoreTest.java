package learn.gjx.tuku1.manager;

import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.entity.AiPictureTask;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

class AiTaskStoreTest {
    private AiTaskStore store; private JdbcTemplate jdbc; private AiEditingProperties config; private String schema;
    private TransactionTemplate tx;
    private static final String MYSQL = System.getenv("MYSQL_TEST_SERVER_URL");
    private Connection admin() throws Exception { return DriverManager.getConnection(MYSQL,System.getenv().getOrDefault("MYSQL_TEST_USERNAME","root"),System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","")); }
    @BeforeEach void setup()throws Exception {
        String url="jdbc:h2:mem:ai_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        boolean mysql=MYSQL!=null&&!MYSQL.isEmpty();
        if(mysql){
            java.net.URI endpoint=java.net.URI.create(MYSQL.substring(5));
            if(!"mysql".equals(endpoint.getScheme())||!Arrays.asList("127.0.0.1","localhost").contains(endpoint.getHost())||!"/".equals(endpoint.getPath())) throw new IllegalArgumentException("Test endpoint must be local and server-only");
            // Reuse the pre-authorized disposable schema prefix; never expand database grants.
            schema="tuku_space_test_ai_"+UUID.randomUUID().toString().replace("-","");
            try(Connection c=admin();Statement s=c.createStatement()){s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");}
            int q=MYSQL.indexOf('?');url=q<0?MYSQL+schema:MYSQL.substring(0,q)+schema+MYSQL.substring(q);
        }
        DriverManagerDataSource ds=new DriverManagerDataSource(url,mysql?System.getenv().getOrDefault("MYSQL_TEST_USERNAME","root"):"sa",mysql?System.getenv().getOrDefault("MYSQL_TEST_PASSWORD",""):"");
        jdbc=new JdbcTemplate(ds);config=new AiEditingProperties();
        String sql=new String(Files.readAllBytes(Paths.get("sql/alter_ai_picture_task.sql")),StandardCharsets.UTF_8);
        if(!mysql)sql=sql.replace("ENGINE=InnoDB","").replace("DEFAULT CHARSET=utf8mb4","");
        ResourceDatabasePopulator populator=new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        populator.execute(ds);populator.execute(ds); // Migration is repeatable.
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        store=new AiTaskStore(jdbc,tx,config);
    }
    @AfterEach void cleanup()throws Exception {if(schema!=null){try(Connection c=admin();Statement s=c.createStatement()){s.execute("DROP DATABASE `"+schema+"`");}schema=null;}}
    private AiPictureTask task(long user) {
        AiPictureTask t=new AiPictureTask();t.setId(UUID.randomUUID().toString());t.setRequestId(UUID.randomUUID().toString());t.setUserId(user);t.setPictureId(11L);
        t.setXScale(2d);t.setYScale(2d);t.setSourceUrl("https://private.invalid/source");t.setStatus("QUEUED");t.setProviderModel("qwen-image-3.0");t.setOutputWidth(1024);t.setOutputHeight(1024);t.setCreateTime(new Date());t.setExpiresAt(new Date(System.currentTimeMillis()+1_800_000));return t;
    }
    @Test void requestIsIdempotentAndBoundToParameters(){
        AiPictureTask first=task(1);store.reserve(first);AiPictureTask retry=task(1);retry.setRequestId(first.getRequestId());
        assertEquals(first.getId(),store.reserve(retry).getId());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_picture_task",Integer.class));
        retry.setXScale(3d);assertThrows(BusinessException.class,()->store.reserve(retry));
    }

    private AiPictureTask succeeded() {
        AiPictureTask t=task(1);store.reserve(t);store.claimGeneration(t.getId(),new Date());store.completed(t.getId(),"https://result.invalid/image.png");return store.find(t.getId(),1L);
    }
    private learn.gjx.tuku1.model.entity.Picture original(AiPictureTask t) {
        learn.gjx.tuku1.model.entity.Picture p=new learn.gjx.tuku1.model.entity.Picture();p.setId(t.getPictureId());p.setUrl(t.getSourceUrl());return p;
    }
    @Test void applyCrashRecoversWithoutGenerationAndOldClaimCannotCommit() {
        AiPictureTask t=succeeded();AiApplyClaim old=store.beginApply(t,1L);
        store.expireStale(new Date(System.currentTimeMillis()+301000));
        AiPictureTask uncertain=store.find(t.getId(),1L);assertEquals("APPLY_UNKNOWN",uncertain.getStatus());
        assertTrue(store.recoverApply(uncertain));assertNull(store.nextQueued());
        AiApplyClaim next=store.beginApply(store.find(t.getId(),1L),1L);
        assertThrows(BusinessException.class,()->tx.execute(s->{store.commitApply(old,original(t),1L);return null;}));
        store.applyUncertain(old);assertEquals("APPLYING",store.find(t.getId(),1L).getStatus());
        tx.execute(s->{store.commitApply(next,original(t),1L);return null;});
        store.applyUncertain(next);assertEquals("APPLIED",store.find(t.getId(),1L).getStatus());
    }
    @Test void appliedStatusRollsBackWithPictureTransactionAndCannotBeForged() {
        AiPictureTask t=succeeded();AiApplyClaim claim=store.beginApply(t,1L);
        assertThrows(IllegalStateException.class,()->store.commitApply(claim,original(t),1L));
        tx.execute(s->{store.commitApply(claim,original(t),1L);s.setRollbackOnly();return null;});
        assertEquals("APPLYING",store.find(t.getId(),1L).getStatus());
        assertThrows(BusinessException.class,()->tx.execute(s->{store.commitApply(claim,original(t),2L);return null;}));
        learn.gjx.tuku1.model.entity.Picture replaced=original(t);replaced.setUrl("different");
        assertThrows(BusinessException.class,()->tx.execute(s->{store.commitApply(claim,replaced,1L);return null;}));
    }
    @Test void legacyUncertainApplyCannotBeRetriedAndExpiredResultCannotBeClaimed() {
        AiPictureTask t=succeeded();store.transition(t.getId(),"SUCCEEDED","APPLY_UNKNOWN","legacy");
        assertFalse(store.recoverApply(store.find(t.getId(),1L)));
        store.transition(t.getId(),"APPLY_UNKNOWN","SUCCEEDED",null);jdbc.update("UPDATE ai_picture_task SET expiresAt=? WHERE id=?",new Date(0),t.getId());
        assertThrows(BusinessException.class,()->store.beginApply(t,1L));
    }
    @Test void taskOwnershipAndStatusCAS(){
        AiPictureTask t=task(1);store.reserve(t);assertNull(store.find(t.getId(),2L));
        assertTrue(store.claimGeneration(t.getId(),new Date()));
        assertTrue(store.transition(t.getId(),"SUBMITTING","UNKNOWN","uncertain"));
        assertFalse(store.transition(t.getId(),"SUBMITTING","FAILED","lost"));
        assertEquals("UNKNOWN",store.latest(11L,1L).getStatus());
    }
    @Test void activeTaskBlocksNewCharge(){store.reserve(task(1));assertThrows(BusinessException.class,()->store.reserve(task(1)));}
    @Test void globalBudgetIncludesFailedAndUnknownSubmissions(){
        config.setDailyGlobalLimit(1);AiPictureTask t=task(1);store.reserve(t);store.claimGeneration(t.getId(),new Date());store.transition(t.getId(),"SUBMITTING","UNKNOWN","x");
        assertThrows(BusinessException.class,()->store.reserve(task(2)));
    }
    @Test void claimIsNeverRequeuedAndTerminalResultIsCAS(){
        AiPictureTask t=task(1);store.reserve(t);
        assertEquals(t.getId(),store.nextQueued().getId());
        assertTrue(store.claimGeneration(t.getId(),new Date()));assertFalse(store.claimGeneration(t.getId(),new Date()));
        assertNull(store.nextQueued());
        assertTrue(store.completed(t.getId(),"https://signed.invalid/result"));
        assertFalse(store.completed(t.getId(),"replacement"));
        assertEquals("SUCCEEDED",store.find(t.getId(),1L).getStatus());
    }
    @Test void crashedClaimBecomesUnknownWithoutLosingQuota(){
        config.setDailyGlobalLimit(1);AiPictureTask t=task(1);store.reserve(t);store.claimGeneration(t.getId(),new Date());
        store.expireStale(new Date(System.currentTimeMillis()+301_000));
        assertEquals("UNKNOWN",store.find(t.getId(),1L).getStatus());assertNull(store.nextQueued());
        assertFalse(store.completed(t.getId(),"late"));assertFalse(store.claimGeneration(t.getId(),new Date()));
        assertThrows(BusinessException.class,()->store.reserve(task(2)));
    }
    @Test void candidateReceiptSurvivesValidationFailureWithoutBecomingUsable(){
        AiPictureTask t=task(1);store.reserve(t);store.claimGeneration(t.getId(),new Date());
        assertTrue(store.received(t.getId(),"https://untrusted.invalid/result","request-1"));
        store.transition(t.getId(),"SUBMITTING","UNKNOWN","RESULT_VALIDATION");
        AiPictureTask saved=store.find(t.getId(),1L);assertEquals("UNKNOWN",saved.getStatus());
        assertEquals("request-1",saved.getProviderRequestId());assertEquals("https://untrusted.invalid/result",saved.getResultUrl());
        assertFalse(store.received(t.getId(),"replacement","request-2"));assertNull(store.nextQueued());
    }
    @Test void unclaimedQueueSurvivesWorkerRestartButExpiredQueueDoesNotSubmit(){
        AiPictureTask t=task(1);store.reserve(t);
        AiTaskStore restarted=new AiTaskStore(jdbc,new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())),config);
        assertEquals(t.getId(),restarted.nextQueued().getId());
        restarted.expireStale(new Date(System.currentTimeMillis()+601_000));
        assertEquals("FAILED",store.find(t.getId(),1L).getStatus());assertNull(store.nextQueued());
    }
    @Test void concurrentWorkersOnlyOneClaims()throws Exception {
        AiPictureTask t=task(1);store.reserve(t);ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<4;i++)futures.add(pool.submit(()->store.claimGeneration(t.getId(),new Date())));
            int wins=0;for(Future<Boolean> f:futures)if(f.get(120,TimeUnit.SECONDS))wins++;
            assertEquals(1,wins);
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDuplicateCreatesOnlyOneRecord()throws Exception {
        AiPictureTask t=task(1);ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Future<AiPictureTask>> futures=new ArrayList<>();
            for(int i=0;i<4;i++){AiPictureTask copy=task(1);copy.setRequestId(t.getRequestId());futures.add(pool.submit(()->store.reserve(copy)));}
            // SSH-forwarded MySQL has several round trips per transaction; this is not a latency benchmark.
            Set<String> ids=new HashSet<>();for(Future<AiPictureTask> f:futures)ids.add(f.get(MYSQL == null ? 5 : 120,TimeUnit.SECONDS).getId());assertEquals(1,ids.size());
        } finally {pool.shutdown();if(!pool.awaitTermination(120,TimeUnit.SECONDS))pool.shutdownNow();}
    }
    @Test void concurrentDifferentRequestsRespectGlobalBudget()throws Exception {
        config.setDailyGlobalLimit(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> a=()->{try{store.reserve(task(1));return true;}catch(BusinessException e){return false;}};
            Callable<Boolean> b=()->{try{store.reserve(task(2));return true;}catch(BusinessException e){return false;}};
            List<Future<Boolean>> results=pool.invokeAll(Arrays.asList(a,b));
            assertNotEquals(results.get(0).get(),results.get(1).get());
        } finally {pool.shutdownNow();}
    }
}
