package learn.gjx.tuku1.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import learn.gjx.tuku1.config.CosClientConfig;
import java.util.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

class PictureFileCleanupManagerTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private CosManager cos;
    private PictureFileCleanupManager manager;
    private final String url = "https://test.cos.ap-test.myqcloud.com/public/1/file.jpg";

    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:cleanup_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);tx = new TransactionTemplate(new DataSourceTransactionManager(ds));cos = mock(CosManager.class);
        String sql = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("sql/alter_public_file_cleanup.sql")), java.nio.charset.StandardCharsets.UTF_8)
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin", "");
        jdbc.execute(sql);jdbc.execute(sql);
        jdbc.execute("CREATE TABLE picture (id bigint PRIMARY KEY, url varchar(512), originalUrl varchar(512), thumbnailUrl varchar(512), isDelete int DEFAULT 0)");
        manager = restarted();
    }

    private PictureFileCleanupManager restarted() {
        PictureFileCleanupManager result = new PictureFileCleanupManager();
        CosClientConfig config = new CosClientConfig();config.setBucket("test");config.setRegion("ap-test");
        ReflectionTestUtils.setField(result,"jdbc",jdbc);ReflectionTestUtils.setField(result,"transactionTemplate",tx);
        ReflectionTestUtils.setField(result,"storage",cos);ReflectionTestUtils.setField(result,"config",config);
        return result;
    }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM public_file_cleanup",Integer.class); }
    private void due() { jdbc.update("UPDATE public_file_cleanup SET nextAttemptTime=?",new Date(0)); }

    @Test void localDateTimeFromMysqlRowDoesNotBlockCleanup() {
        manager.deleteQuietly(url, "test");
        due();
        String id = jdbc.queryForObject("SELECT id FROM public_file_cleanup", String.class);
        JdbcTemplate mysqlRows = spy(jdbc);
        doReturn(Collections.singletonList(Map.of("url", url, "attempts", 0,
                "nextAttemptTime", java.time.LocalDateTime.of(2000, 1, 1, 0, 0))))
                .when(mysqlRows).queryForList(
                        "SELECT url,attempts,nextAttemptTime FROM public_file_cleanup WHERE id=? FOR UPDATE", id);
        ReflectionTestUtils.setField(manager, "jdbc", mysqlRows);

        manager.retryFailedDeletes();

        assertEquals(0, count());
        verify(cos).deleteObject("public/1/file.jpg");
    }

    @Test
    void shouldRetryTransientCosDeleteFailure() {
        doThrow(new IllegalStateException("temporary failure"))
                .doNothing().when(cos).deleteObject("public/1/file.jpg");
        manager.deleteQuietly(url, "test");
        manager.retryFailedDeletes();
        assertEquals(1,count());
        due();restarted().retryFailedDeletes();
        assertEquals(0,count());verify(cos,times(2)).deleteObject("public/1/file.jpg");
    }

    @Test void preUploadReceiptSurvivesRestartAndBusinessRollback() {
        manager.registerUpload(Collections.singleton(url));
        tx.execute(s -> {manager.adopt(Collections.singleton(url));assertEquals(0,count());s.setRollbackOnly();return null;});
        assertEquals(1,count());due();restarted().retryFailedDeletes();assertEquals(0,count());verify(cos).deleteObject("public/1/file.jpg");
    }
    @Test void adoptionAndOldFileOutboxRollbackTogether() {
        manager.registerUpload(Collections.singleton(url));
        String old=url.replace("file.jpg","old.jpg");
        tx.execute(s -> {manager.adopt(Collections.singleton(url));manager.enqueueCommitted(Collections.singleton(old));s.setRollbackOnly();return null;});
        assertEquals(url,jdbc.queryForObject("SELECT url FROM public_file_cleanup",String.class));
        tx.execute(s -> {manager.adopt(Collections.singleton(url));manager.enqueueCommitted(Collections.singleton(old));return null;});
        restarted().retryFailedDeletes();verify(cos).deleteObject("public/1/old.jpg");verify(cos,never()).deleteObject("public/1/file.jpg");
    }
    @Test void committedReferencesAreNeverDeletedAfterAmbiguousResponse() {
        jdbc.update("INSERT INTO picture (id,url) VALUES (1,?)",url);
        manager.deleteQuietly(url,"response-lost");restarted().retryFailedDeletes();assertEquals(0,count());verifyNoInteractions(cos);
    }
    @Test void retryIsNeverDiscardedAndExpiredReceiptsCannotBeAdopted() {
        manager.registerUpload(Collections.singleton(url));due();
        assertThrows(RuntimeException.class,()->tx.execute(s->{manager.adopt(Collections.singleton(url));return null;}));
        doThrow(new IllegalStateException("offline")).when(cos).deleteObject(anyString());
        for(int i=0;i<8;i++){due();restarted().retryFailedDeletes();}
        assertEquals(1,count());assertEquals(8,jdbc.queryForObject("SELECT attempts FROM public_file_cleanup",Integer.class));
    }
    @Test void databaseFailureDoesNotBecomeNoReferencesOrDeleteAnything() {
        manager.deleteQuietly(url,"test");jdbc.execute("DROP TABLE picture");manager.retryFailedDeletes();
        assertEquals(1,count());verifyNoInteractions(cos);
    }
    @Test void retryBackoffMustNotRenewAnExpiredUploadReceipt() {
        manager.registerUpload(Collections.singleton(url));
        due();
        doThrow(new IllegalStateException("delete response lost")).when(cos).deleteObject(anyString());
        restarted().retryFailedDeletes();
        assertTrue(jdbc.queryForObject("SELECT nextAttemptTime FROM public_file_cleanup", Date.class).after(new Date()));
        assertThrows(learn.gjx.tuku1.exception.BusinessException.class,
                () -> tx.execute(s -> { restarted().adopt(Collections.singleton(url)); return null; }));
        assertEquals(1, count());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM public_file_cleanup", Integer.class));
    }
    @Test void foreignAndPrivatePathsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->manager.registerUpload(Collections.singleton("https://other.invalid/public/x.jpg")));
        assertThrows(IllegalArgumentException.class,()->manager.registerUpload(Collections.singleton(url.replace("/public/","/space/"))));
        assertEquals(0,count());verifyNoInteractions(cos);
    }
}
