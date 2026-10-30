-- R8: 部署前备份；回滚（确认不再需要收藏数据后）：DROP TABLE picture_favorite;
CREATE TABLE IF NOT EXISTS picture_favorite (
    userId BIGINT NOT NULL,
    pictureId BIGINT NOT NULL,
    createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (userId, pictureId),
    KEY idx_favorite_picture (pictureId),
    KEY idx_favorite_user_time (userId, createTime, pictureId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
