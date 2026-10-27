package learn.gjx.tuku1.manager.cache;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable invalidation: the revision changes atomically with picture mutations. */
@Component
public class PictureListRevision {
    private final JdbcTemplate jdbcTemplate;

    public PictureListRevision(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long current() {
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM picture_list_revision WHERE id = 1", Long.class);
        if (revision == null || revision < 0) {
            throw new IllegalStateException("公开图片列表版本号不存在或无效，请执行版本表迁移");
        }
        return revision;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void advance() {
        if (jdbcTemplate.update("UPDATE picture_list_revision SET revision = revision + 1 WHERE id = 1") != 1) {
            throw new IllegalStateException("公开图片列表版本更新失败");
        }
    }
}
