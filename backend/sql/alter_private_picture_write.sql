-- 第七章第四组：部署新后端前执行。先备份，不改变历史图片可见性或空间用量。
-- 已有 spaceId 非空的业务记录必须先人工核对存储私有性和 totalCount/totalSize；不可直接启用。
CREATE TABLE IF NOT EXISTS private_file_cleanup (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spaceId BIGINT NOT NULL,
    url VARCHAR(512) NOT NULL,
    nextAttemptTime DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    INDEX idx_private_cleanup_due (nextAttemptTime),
    INDEX idx_private_cleanup_space_url (spaceId, url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
