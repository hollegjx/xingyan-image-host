CREATE TABLE IF NOT EXISTS public_file_cleanup (
    id varchar(36) NOT NULL PRIMARY KEY,
    url varchar(512) NOT NULL,
    nextAttemptTime datetime(3) NOT NULL,
    attempts int NOT NULL DEFAULT 0,
    UNIQUE KEY uk_public_cleanup_url (url),
    KEY idx_public_cleanup_due (nextAttemptTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
