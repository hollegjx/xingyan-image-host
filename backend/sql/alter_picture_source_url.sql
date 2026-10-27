use tuku;

ALTER TABLE picture
    ADD COLUMN sourceUrl VARCHAR(1024) NULL COMMENT '原始图片来源 URL，仅内部使用'
        AFTER url;
