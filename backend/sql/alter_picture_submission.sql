-- R6: 备份后新增来源关联，不修改原图。代码回滚保留本表及已有投稿。
-- 仅另获用户确认并归档后可执行：DROP TABLE picture_submission;
CREATE TABLE IF NOT EXISTS picture_submission (
    sourcePictureId BIGINT NOT NULL,
    publicPictureId BIGINT NOT NULL,
    createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (sourcePictureId, publicPictureId),
    UNIQUE KEY uk_submission_public (publicPictureId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
