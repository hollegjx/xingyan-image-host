-- 在目标图库数据库中执行；幂等，只增加一张版本表，不修改图片记录。
CREATE TABLE IF NOT EXISTS picture_list_revision (
    id TINYINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='公开图片列表缓存版本；必须与图片变更同事务更新';
INSERT IGNORE INTO picture_list_revision (id, revision) VALUES (1, 0);
