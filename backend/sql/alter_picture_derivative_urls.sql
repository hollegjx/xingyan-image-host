USE tuku;

-- 第六章第 4 步：明确原图、主展示图和缩略图字段。
-- 现有 url 中保存的是原始上传文件，因此先复制到 originalUrl；thumbnailUrl 留空，
-- 后续生成派生图后再分别写入 url 和 thumbnailUrl。
ALTER TABLE picture
    ADD COLUMN originalUrl VARCHAR(512) NULL COMMENT 'COS 原始文件 URL，用于重新处理和下载' AFTER url,
    ADD COLUMN thumbnailUrl VARCHAR(512) NULL COMMENT '列表缩略图 URL；为空时回退使用 url' AFTER originalUrl;

UPDATE picture
SET originalUrl = url, updateTime = updateTime
WHERE originalUrl IS NULL OR originalUrl = '';

ALTER TABLE picture
    MODIFY COLUMN url VARCHAR(512) NOT NULL COMMENT '主展示图 URL；派生图未生成时回退为原图',
    MODIFY COLUMN originalUrl VARCHAR(512) NOT NULL COMMENT 'COS 原始文件 URL，用于重新处理和下载',
    MODIFY COLUMN sourceUrl VARCHAR(1024) NULL COMMENT '外部图片来源 URL，仅用于追踪来源';
