-- Additive and repeatable. Select the intended database before executing.
CREATE TABLE IF NOT EXISTS ai_picture_gate (
    id int NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO ai_picture_gate (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM ai_picture_gate WHERE id=1);
CREATE TABLE IF NOT EXISTS ai_picture_task (
    id varchar(36) NOT NULL PRIMARY KEY,
    userId bigint NOT NULL,
    pictureId bigint NOT NULL,
    requestId varchar(36) NOT NULL,
    xScale double NOT NULL,
    yScale double NOT NULL,
    sourceUrl varchar(1024) NOT NULL,
    providerTaskId varchar(128) DEFAULT NULL,
    providerModel varchar(64) DEFAULT NULL,
    providerRequestId varchar(128) DEFAULT NULL,
    outputWidth int DEFAULT NULL,
    outputHeight int DEFAULT NULL,
    status varchar(24) NOT NULL,
    resultUrl varchar(2048) DEFAULT NULL,
    message varchar(200) DEFAULT NULL,
    createTime datetime NOT NULL,
    expiresAt datetime NOT NULL,
    nextPollTime datetime NOT NULL,
    UNIQUE KEY uk_ai_request (userId, requestId),
    KEY idx_ai_user_created (userId, createTime),
    KEY idx_ai_picture (userId, pictureId, createTime),
    KEY idx_ai_created (createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
