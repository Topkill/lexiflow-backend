CREATE TABLE IF NOT EXISTS `word_ai_qa` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '问答结果 ID',
  `created_by_user_id` INT NULL COMMENT '首次生成用户 ID，跨用户缓存命中时仅作来源记录',
  `word_id` INT NOT NULL COMMENT '单词 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `question` VARCHAR(512) NOT NULL COMMENT '用户问题',
  `source_hash` CHAR(64) NOT NULL COMMENT '输入上下文 hash',
  `cache_key` VARCHAR(255) NOT NULL COMMENT '缓存 key，后端按单词、问题和提示词生成',
  `content_json` JSON NOT NULL COMMENT 'AI 问答结构化内容',
  `output_schema_json` JSON NULL COMMENT '输出 JSON 结构',
  `cache_active` TINYINT(1) NULL DEFAULT 1 COMMENT '当前可命中缓存，1 是，NULL 历史版本',
  `hit_count` INT NOT NULL DEFAULT 0 COMMENT '命中次数',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word_ai_qa_active_cache` (`cache_key`, `cache_active`, `deleted`),
  KEY `idx_word_ai_qa_word` (`wordbook_id`, `word_id`),
  KEY `idx_word_ai_qa_creator_time` (`created_by_user_id`, `created_at`),
  KEY `idx_word_ai_qa_source` (`source_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 单词问答结果表';

ALTER TABLE `cloze_quiz`
  ADD COLUMN `source_hash` CHAR(64) NULL COMMENT '生成输入上下文 hash' AFTER `source_type`,
  ADD COLUMN `cache_key` VARCHAR(255) NULL COMMENT '缓存 key，后端按完形上下文生成' AFTER `source_hash`,
  ADD COLUMN `cache_active` TINYINT(1) NULL DEFAULT 1 COMMENT '当前可命中缓存，1 是，NULL 历史版本' AFTER `cache_key`,
  ADD COLUMN `hit_count` INT NOT NULL DEFAULT 0 COMMENT '命中次数' AFTER `cache_active`,
  ADD UNIQUE KEY `uk_cloze_quiz_active_cache` (`cache_key`, `cache_active`, `deleted`),
  ADD KEY `idx_cloze_quiz_source` (`source_hash`);

ALTER TABLE `ai_call_log`
  MODIFY COLUMN `content_type` VARCHAR(32) NOT NULL COMMENT 'AI 内容类型：WORD_QA、CLOZE、CLOZE_REVIEW、REPORT';

ALTER TABLE `async_task`
  ADD KEY `idx_async_task_result` (`task_type`, `result_id`),
  MODIFY COLUMN `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型：AI_WORD_QA、AI_CLOZE、AI_CLOZE_REVIEW、AI_REPORT',
  MODIFY COLUMN `result_id` INT NULL COMMENT '结果 ID：AI_WORD_QA 对应 word_ai_qa.id，AI_CLOZE 对应 cloze_quiz.id，AI_CLOZE_REVIEW 对应 cloze_attempt_ai_review.id';
