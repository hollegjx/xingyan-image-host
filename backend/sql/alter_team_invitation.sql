-- R5: 新增邀请表；部署前备份。回滚（确认不再需要邀请数据后）：DROP TABLE team_invitation;
CREATE TABLE IF NOT EXISTS team_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    spaceId BIGINT NOT NULL,
    creatorId BIGINT NOT NULL,
    tokenHash CHAR(64) NOT NULL,
    spaceRole VARCHAR(16) NOT NULL,
    expiresAt DATETIME NOT NULL,
    maxUses INT NULL,
    usedCount INT NOT NULL DEFAULT 0,
    revoked TINYINT NOT NULL DEFAULT 0,
    createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_invitation_hash (tokenHash),
    KEY idx_team_invitation_space (spaceId, revoked, expiresAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
