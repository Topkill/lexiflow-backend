CREATE TABLE IF NOT EXISTS `study_note` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '学习笔记 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '来源类型：NORMAL 普通笔记，WORD_QA AI 单词问答摘录，CLOZE_REVIEW AI 完形评阅摘录',
  `source_id` INT NULL COMMENT '来源业务结果 ID：WORD_QA 对应 word_ai_qa.id，CLOZE_REVIEW 对应 cloze_attempt_ai_review.id，NORMAL 为空',
  `wordbook_id` INT NULL COMMENT '关联词库 ID',
  `word_id` INT NULL COMMENT '关联单词 ID',
  `title` VARCHAR(255) NOT NULL COMMENT '笔记标题',
  `quoted_text` MEDIUMTEXT NULL COMMENT '引用快照文本，保存用户当时看到或选中的内容',
  `content_md` MEDIUMTEXT NULL COMMENT '用户笔记 Markdown，前端渲染时禁止 HTML',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_study_note_user_time` (`user_id`, `created_at`),
  KEY `idx_study_note_source` (`source_type`, `source_id`),
  KEY `idx_study_note_word` (`user_id`, `wordbook_id`, `word_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习笔记表';
