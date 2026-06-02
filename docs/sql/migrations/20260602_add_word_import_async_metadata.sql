ALTER TABLE `word_import_task`
  MODIFY COLUMN `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名或来源 URL',
  MODIFY COLUMN `file_path` VARCHAR(512) NOT NULL COMMENT '本地文件路径或远程 URL',
  ADD COLUMN `source_type` VARCHAR(32) NOT NULL DEFAULT 'EXCEL' COMMENT '导入来源：EXCEL、JSON_URL' AFTER `file_path`,
  ADD COLUMN `request_json` JSON NULL COMMENT '导入请求参数' AFTER `source_type`,
  ADD KEY `idx_word_import_source_type` (`source_type`);
