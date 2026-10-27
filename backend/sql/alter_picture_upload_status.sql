USE tuku;

-- 已有图片视为已完成，新上传图片由应用显式保存为草稿。
ALTER TABLE picture
    ADD COLUMN uploadStatus TINYINT DEFAULT 1 NOT NULL COMMENT '上传状态：0-草稿；1-已完成' AFTER userId;

CREATE INDEX idx_uploadStatus ON picture (uploadStatus);

ALTER TABLE picture
    ALTER COLUMN uploadStatus SET DEFAULT 0;
