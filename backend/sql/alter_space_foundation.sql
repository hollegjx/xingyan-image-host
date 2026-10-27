-- 在已完成第六章迁移的目标数据库执行；picture 的 ALTER 是一次性迁移。
-- 不修改历史图片：spaceId 默认 NULL，仍表示公共图库。
CREATE TABLE IF NOT EXISTS space (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '空间 id',
    spaceName VARCHAR(128) NOT NULL COMMENT '空间名称',
    spaceLevel INT NOT NULL DEFAULT 0 COMMENT '0-普通版；1-专业版；2-旗舰版',
    maxSize BIGINT NOT NULL DEFAULT 104857600 COMMENT '容量上限，字节',
    maxCount BIGINT NOT NULL DEFAULT 100 COMMENT '图片数量上限',
    totalSize BIGINT NOT NULL DEFAULT 0 COMMENT '已用容量，字节',
    totalCount BIGINT NOT NULL DEFAULT 0 COMMENT '已用图片数量',
    userId BIGINT NOT NULL COMMENT '空间创建人',
    createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    editTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete TINYINT NOT NULL DEFAULT 0,
    INDEX idx_spaceName (spaceName),
    INDEX idx_spaceLevel (spaceLevel),
    INDEX idx_space_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户空间';

ALTER TABLE picture ADD COLUMN spaceId BIGINT NULL COMMENT '空间 id；NULL 为公共图库' AFTER userId;
CREATE INDEX idx_spaceId ON picture(spaceId);
