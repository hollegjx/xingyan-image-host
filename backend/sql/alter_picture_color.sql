-- MySQL 5.7+; select the intended database before running. No existing image is rewritten.
-- Repeatable migration. Deploy this BEFORE a JAR that selects picture.picColor.
SET @add_pic_color = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'picture' AND column_name = 'picColor') = 0,
    'ALTER TABLE picture ADD COLUMN picColor VARCHAR(7) NULL COMMENT ''主色调 #RRGGBB''',
    'SELECT 1');
PREPARE pic_color_stmt FROM @add_pic_color;
EXECUTE pic_color_stmt;
DEALLOCATE PREPARE pic_color_stmt;
