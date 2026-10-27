-- Existing task IDs, UNKNOWN records and quota history must be preserved.
-- Run in the selected application database before deploying the Qwen worker.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_picture_task' AND column_name='providerRequestId')=0,
 'ALTER TABLE ai_picture_task ADD COLUMN providerRequestId varchar(128) DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_picture_task' AND column_name='providerModel')=0,
 'ALTER TABLE ai_picture_task ADD COLUMN providerModel varchar(64) DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_picture_task' AND column_name='outputWidth')=0,
 'ALTER TABLE ai_picture_task ADD COLUMN outputWidth int DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_picture_task' AND column_name='outputHeight')=0,
 'ALTER TABLE ai_picture_task ADD COLUMN outputHeight int DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
