-- Additive, repeatable MySQL migration. Select the target DB and back it up first.
-- Existing spaces stay private; public picture.spaceId remains NULL. No object ACL changes.
SET @team_column = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='space' AND column_name='spaceType')=0,
 'ALTER TABLE space ADD COLUMN spaceType INT NOT NULL DEFAULT 0 COMMENT ''0-private,1-team''', 'SELECT 1');
PREPARE team_column_stmt FROM @team_column;
EXECUTE team_column_stmt;
DEALLOCATE PREPARE team_column_stmt;
SET @team_index = IF((SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='space' AND index_name='idx_space_owner_type')=0,
 'CREATE INDEX idx_space_owner_type ON space(userId,spaceType,isDelete)', 'SELECT 1');
PREPARE team_index_stmt FROM @team_index;
EXECUTE team_index_stmt;
DEALLOCATE PREPARE team_index_stmt;
CREATE TABLE IF NOT EXISTS space_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 spaceId BIGINT NOT NULL,
 userId BIGINT NOT NULL,
 spaceRole VARCHAR(16) NOT NULL DEFAULT 'viewer',
 createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_space_user(spaceId,userId),
 INDEX idx_space_user_user(userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
