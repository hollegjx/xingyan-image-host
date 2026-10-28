-- Keep legacy unknown tasks quarantined. Run before deploying the new application.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_picture_task' AND column_name='applyToken')=0,
 'ALTER TABLE ai_picture_task ADD COLUMN applyToken varchar(36) DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
