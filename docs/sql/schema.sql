-- LexiFlow MySQL 8 schema
-- Database: lexiflow
-- Charset: utf8mb4
-- Collation: utf8mb4_0900_ai_ci
-- Physical foreign keys are intentionally omitted. Relationships are enforced by application logic and indexes.

CREATE DATABASE IF NOT EXISTS `lexiflow`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `lexiflow`;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `users` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
  `email` VARCHAR(128) NOT NULL COMMENT '邮箱，统一小写存储',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `nickname` VARCHAR(64) NOT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(512) NULL COMMENT '头像地址',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色：USER、ADMIN',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、DISABLED、LOCKED',
  `token_version` INT NOT NULL DEFAULT 1 COMMENT '用户级 Token 版本，递增后旧 token 失效',
  `last_login_at` DATETIME(3) NULL COMMENT '最近登录时间',
  `last_login_ip` VARCHAR(64) NULL COMMENT '最近登录 IP',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email_deleted` (`email`, `deleted`),
  KEY `idx_users_role` (`role`),
  KEY `idx_users_status` (`status`),
  KEY `idx_users_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号与基础资料表';

CREATE TABLE IF NOT EXISTS `user_settings` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '设置 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `target_exam` VARCHAR(32) NULL COMMENT '目标考试：CET4、CET6、POSTGRADUATE',
  `daily_new_words` INT NOT NULL DEFAULT 30 COMMENT '默认每日新词数量',
  `ai_key_mode` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT 'AI Key 使用模式：PUBLIC、PRIVATE',
  `enable_daily_report` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用日报入口，0 否，1 是',
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '用户时区',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_settings_user` (`user_id`, `deleted`),
  KEY `idx_user_settings_target_exam` (`target_exam`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户学习偏好与 AI 使用偏好表';

CREATE TABLE IF NOT EXISTS `user_ai_config` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '配置 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `api_base_url` VARCHAR(512) NOT NULL COMMENT 'OpenAI 兼容接口 Base URL',
  `encrypted_api_key` VARCHAR(1024) NOT NULL COMMENT 'AES 加密后的 API Key',
  `model_name` VARCHAR(128) NOT NULL COMMENT '模型名称',
  `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '默认温度',
  `stream_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否使用流式输出，0 否，1 是',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用，0 否，1 是',
  `last_verified_at` DATETIME(3) NULL COMMENT '最近验证时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_ai_config_user` (`user_id`, `deleted`),
  KEY `idx_user_ai_config_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户私有 AI 配置表';

CREATE TABLE IF NOT EXISTS `wordbook` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '词库 ID',
  `name` VARCHAR(128) NOT NULL COMMENT '词库名称',
  `code` VARCHAR(64) NOT NULL COMMENT '词库编码',
  `type` VARCHAR(32) NOT NULL COMMENT '词库类型：CET4、CET6、POSTGRADUATE',
  `description` VARCHAR(512) NULL COMMENT '词库描述',
  `cover_url` VARCHAR(512) NULL COMMENT '封面图',
  `difficulty_level` INT NOT NULL DEFAULT 1 COMMENT '难度等级，1-5',
  `word_count` INT NOT NULL DEFAULT 0 COMMENT '单词数量冗余',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用，0 否，1 是',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '展示排序',
  `created_by` INT NULL COMMENT '创建人',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wordbook_code_deleted` (`code`, `deleted`),
  KEY `idx_wordbook_type` (`type`),
  KEY `idx_wordbook_enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词库表';

CREATE TABLE IF NOT EXISTS `word` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '单词 ID',
  `wordbook_id` INT NOT NULL COMMENT '所属词库 ID',
  `word` VARCHAR(128) NOT NULL COMMENT '单词展示值，来自词典 JSON 的 word 字段',
  `normalized_word` VARCHAR(128) NOT NULL COMMENT '规范化单词，用于同词书内去重和检索',
  `phonetic0` VARCHAR(128) NULL COMMENT '音标 0，按词典源约定通常为英音',
  `phonetic1` VARCHAR(128) NULL COMMENT '音标 1，按词典源约定通常为美音',
  `trans` JSON NOT NULL COMMENT '释义 JSON，结构如 [{pos, cn}]',
  `sentences` JSON NULL COMMENT '例句 JSON，结构如 [{c, cn}]',
  `phrases` JSON NULL COMMENT '短语 JSON，结构如 [{c, cn}]',
  `synos` JSON NULL COMMENT '同近义词 JSON，结构如 [{pos, cn, ws}]',
  `rel_words` JSON NULL COMMENT '相关词 JSON，结构如 {root, rels}',
  `etymology` JSON NULL COMMENT '词源 JSON，结构如 [{t, d}]',
  `primary_pos` VARCHAR(32) NULL COMMENT '主要词性，便于列表展示',
  `primary_definition` VARCHAR(512) NULL COMMENT '主释义，便于检索和展示',
  `tags` VARCHAR(512) NULL COMMENT '逗号分隔标签',
  `sequence_no` INT NOT NULL DEFAULT 0 COMMENT '词库内学习顺序',
  `difficulty_level` INT NOT NULL DEFAULT 1 COMMENT '词库内难度，1-5',
  `exam_frequency` INT NOT NULL DEFAULT 0 COMMENT '考频或权重',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用，0 否，1 是',
  `created_by` INT NULL COMMENT '创建人',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word_wordbook_normalized_deleted` (`wordbook_id`, `normalized_word`, `deleted`),
  UNIQUE KEY `uk_word_wordbook_sequence_deleted` (`wordbook_id`, `sequence_no`, `deleted`),
  KEY `idx_word_wordbook_enabled_seq` (`wordbook_id`, `enabled`, `sequence_no`),
  KEY `idx_word_normalized` (`normalized_word`),
  KEY `idx_word_primary_pos` (`primary_pos`),
  KEY `idx_word_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词书内单词表';

CREATE TABLE IF NOT EXISTS `word_import_task` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '导入任务 ID',
  `async_task_id` INT NULL COMMENT '对应异步任务 ID',
  `wordbook_id` INT NOT NULL COMMENT '目标词库 ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名或来源 URL',
  `file_path` VARCHAR(512) NOT NULL COMMENT '本地文件路径或远程 URL',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'EXCEL' COMMENT '导入来源：EXCEL、JSON_URL',
  `request_json` JSON NULL COMMENT '导入请求参数',
  `duplicate_strategy` VARCHAR(32) NOT NULL DEFAULT 'SKIP' COMMENT '重复处理策略：SKIP、OVERWRITE、FILL_EMPTY',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '导入状态：PENDING、RUNNING、SUCCESS、PARTIAL_SUCCESS、FAILED',
  `total_rows` INT NOT NULL DEFAULT 0 COMMENT '总行数',
  `success_rows` INT NOT NULL DEFAULT 0 COMMENT '成功行数',
  `failed_rows` INT NOT NULL DEFAULT 0 COMMENT '失败行数',
  `error_report_path` VARCHAR(512) NULL COMMENT '错误报告路径',
  `started_at` DATETIME(3) NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `created_by` INT NOT NULL COMMENT '创建人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_word_import_wordbook` (`wordbook_id`),
  KEY `idx_word_import_source_type` (`source_type`),
  KEY `idx_word_import_status` (`status`),
  KEY `idx_word_import_created_by` (`created_by`),
  KEY `idx_word_import_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词库导入任务表';

CREATE TABLE IF NOT EXISTS `word_import_error` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '错误 ID',
  `import_task_id` INT NOT NULL COMMENT '导入任务 ID',
  `row_no` INT NOT NULL COMMENT 'Excel 行号',
  `word_text` VARCHAR(128) NULL COMMENT '行内单词',
  `error_code` VARCHAR(64) NOT NULL COMMENT '错误编码',
  `error_message` VARCHAR(512) NOT NULL COMMENT '错误说明',
  `raw_data` JSON NULL COMMENT '原始行数据',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_word_import_error_task` (`import_task_id`),
  KEY `idx_word_import_error_row` (`import_task_id`, `row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词库导入错误明细表';

CREATE TABLE IF NOT EXISTS `study_plan` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '计划 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `name` VARCHAR(128) NOT NULL COMMENT '计划名称',
  `new_words_per_group` INT NOT NULL DEFAULT 20 COMMENT '每组新词数量',
  `review_words_per_group` INT NOT NULL DEFAULT 40 COMMENT '每组复习词数量',
  `start_date` DATE NOT NULL COMMENT '计划开始日期',
  `expected_finish_date` DATE NULL COMMENT '预计完成日期',
  `actual_finish_date` DATE NULL COMMENT '实际完成日期',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '计划状态：ACTIVE、PAUSED、COMPLETED、ENDED',
  `total_words` INT NOT NULL DEFAULT 0 COMMENT '计划总词数',
  `learned_count` INT NOT NULL DEFAULT 0 COMMENT '已学习新词数',
  `reviewed_count` INT NOT NULL DEFAULT 0 COMMENT '累计复习次数',
  `mastered_count` INT NOT NULL DEFAULT 0 COMMENT '已掌握词数',
  `current_sequence_no` INT NOT NULL DEFAULT 0 COMMENT '当前学习到的词库顺序',
  `is_primary` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否主计划，0 否，1 是',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  KEY `idx_study_plan_user_status` (`user_id`, `status`),
  KEY `idx_study_plan_user_primary` (`user_id`, `is_primary`, `deleted`),
  KEY `idx_study_plan_wordbook` (`wordbook_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习计划表';

CREATE TABLE IF NOT EXISTS `daily_task` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '今日任务 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `plan_id` INT NOT NULL COMMENT '学习计划 ID',
  `task_date` DATE NOT NULL COMMENT '任务日期',
  `group_no` INT NOT NULL DEFAULT 1 COMMENT '当天第几组学习任务',
  `task_type` VARCHAR(32) NOT NULL DEFAULT 'DAILY' COMMENT '任务类型：DAILY、WRONG_WORD_PRACTICE',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING、DONE、EXPIRED',
  `new_count` INT NOT NULL DEFAULT 0 COMMENT '本组新词数量',
  `review_count` INT NOT NULL DEFAULT 0 COMMENT '本组复习数量',
  `extra_count` INT NOT NULL DEFAULT 0 COMMENT '额外学习数量',
  `done_count` INT NOT NULL DEFAULT 0 COMMENT '已完成 item 数',
  `skipped_count` INT NOT NULL DEFAULT 0 COMMENT '跳过 item 数',
  `completed_at` DATETIME(3) NULL COMMENT '本组单词完成时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_task_user_plan_date_group_type` (`user_id`, `plan_id`, `task_date`, `group_no`, `task_type`, `deleted`),
  KEY `idx_daily_task_user_date` (`user_id`, `task_date`),
  KEY `idx_daily_task_user_type_date` (`user_id`, `task_type`, `task_date`),
  KEY `idx_daily_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日任务主表';

CREATE TABLE IF NOT EXISTS `daily_task_item` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '任务明细 ID',
  `daily_task_id` INT NOT NULL COMMENT '今日任务 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID，冗余便于查询',
  `plan_id` INT NOT NULL COMMENT '计划 ID，冗余便于查询',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `word_id` INT NOT NULL COMMENT '单词 ID',
  `item_type` VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT '任务项类型：NEW、REVIEW、EXTRA',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务项状态：PENDING、DONE、SKIPPED',
  `sequence_no` INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `feedback` VARCHAR(32) NULL COMMENT '最近反馈：UNKNOWN、KNOWN',
  `done_at` DATETIME(3) NULL COMMENT '完成时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_item_task_word_type` (`daily_task_id`, `word_id`, `item_type`, `deleted`),
  KEY `idx_daily_item_task_status` (`daily_task_id`, `status`),
  KEY `idx_daily_item_user_date` (`user_id`, `created_at`),
  KEY `idx_daily_item_word` (`word_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日任务明细表';

CREATE TABLE IF NOT EXISTS `user_word_state` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '状态 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `word_id` INT NOT NULL COMMENT '单词 ID',
  `plan_id` INT NULL COMMENT '最近关联计划 ID',
  `mastery_status` VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT '当前掌握状态：NEW、LEARNING、REVIEWING、MASTERED、DIFFICULT',
  `learned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已学习，0 否，1 是',
  `repetition` INT NOT NULL DEFAULT 0 COMMENT 'SM-2 连续成功复习次数',
  `interval_days` INT NOT NULL DEFAULT 0 COMMENT '当前复习间隔天数',
  `easiness_factor` DECIMAL(4,2) NOT NULL DEFAULT 2.50 COMMENT 'SM-2 难度因子',
  `next_review_date` DATE NULL COMMENT '下次复习日期',
  `last_feedback` VARCHAR(32) NULL COMMENT '最近反馈：UNKNOWN、KNOWN',
  `last_studied_at` DATETIME(3) NULL COMMENT '最近学习时间',
  `last_reviewed_at` DATETIME(3) NULL COMMENT '最近复习时间',
  `wrong_count` INT NOT NULL DEFAULT 0 COMMENT '错误次数冗余',
  `correct_count` INT NOT NULL DEFAULT 0 COMMENT '正确次数冗余',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_word_state_scope` (`user_id`, `wordbook_id`, `word_id`, `deleted`),
  KEY `idx_user_word_next_review` (`user_id`, `wordbook_id`, `next_review_date`),
  KEY `idx_user_word_mastery` (`user_id`, `mastery_status`),
  KEY `idx_user_word_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户单词当前掌握状态表';

CREATE TABLE IF NOT EXISTS `study_event` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '事件 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `plan_id` INT NULL COMMENT '计划 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `word_id` INT NOT NULL COMMENT '单词 ID',
  `daily_task_id` INT NULL COMMENT '今日任务 ID',
  `daily_task_item_id` INT NULL COMMENT '任务明细 ID',
  `scene` VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT '学习场景：NEW、REVIEW、EXTRA、QUIZ',
  `feedback` VARCHAR(32) NULL COMMENT '反馈：UNKNOWN、KNOWN',
  `quality_score` INT NULL COMMENT '映射到 SM-2 的质量分',
  `is_correct` TINYINT(1) NULL COMMENT '测验场景是否正确，0 否，1 是，空表示非测验或未判定',
  `duration_seconds` INT NULL COMMENT '停留或作答耗时',
  `source_ref_id` BIGINT NULL COMMENT '测验、报告等来源 ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_study_event_user_time` (`user_id`, `created_at`),
  KEY `idx_study_event_time_user` (`created_at`, `user_id`),
  KEY `idx_study_event_user_wordbook_time_correct` (`user_id`, `wordbook_id`, `created_at`, `is_correct`),
  KEY `idx_study_event_word` (`user_id`, `wordbook_id`, `word_id`),
  KEY `idx_study_event_scene` (`scene`),
  KEY `idx_study_event_task` (`daily_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习事件表';

CREATE TABLE IF NOT EXISTS `wrong_word` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '错词 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `word_id` INT NOT NULL COMMENT '单词 ID',
  `wrong_count` INT NOT NULL DEFAULT 1 COMMENT '错误次数',
  `last_source` VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT '最近来源：NEW、REVIEW、EXTRA、QUIZ',
  `last_event_id` INT NULL COMMENT '最近错误事件 ID',
  `last_wrong_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近错误时间',
  `resolved` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已解决，0 否，1 是',
  `resolved_at` DATETIME(3) NULL COMMENT '解决时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wrong_word_scope` (`user_id`, `wordbook_id`, `word_id`, `deleted`),
  KEY `idx_wrong_word_user_count` (`user_id`, `wrong_count`),
  KEY `idx_wrong_word_last_wrong` (`user_id`, `last_wrong_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错词表';

CREATE TABLE IF NOT EXISTS `favorite_word` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '收藏 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `word_id` INT NOT NULL COMMENT '单词 ID',
  `note` VARCHAR(512) NULL COMMENT '用户备注',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_word_scope` (`user_id`, `wordbook_id`, `word_id`, `deleted`),
  KEY `idx_favorite_word_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏词表';

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

CREATE TABLE IF NOT EXISTS `ai_public_config` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '配置 ID',
  `name` VARCHAR(128) NOT NULL COMMENT '配置名称',
  `api_base_url` VARCHAR(512) NOT NULL COMMENT 'OpenAI 兼容 Base URL',
  `encrypted_api_key` VARCHAR(1024) NOT NULL COMMENT 'AES 加密 API Key',
  `model_name` VARCHAR(128) NOT NULL COMMENT '模型名称',
  `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '温度',
  `stream_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否使用流式输出，0 否，1 是',
  `daily_quota_per_user` INT NOT NULL DEFAULT 20 COMMENT '每用户每日公共调用配额',
  `active` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前启用配置，0 否，1 是',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可用，0 否，1 是',
  `remark` VARCHAR(512) NULL COMMENT '备注',
  `created_by` INT NOT NULL COMMENT '创建人',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  KEY `idx_ai_public_active` (`active`, `enabled`),
  KEY `idx_ai_public_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员公共 AI 配置表';

CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '提示词模板 ID',
  `feature_type` VARCHAR(64) NOT NULL COMMENT 'AI 功能类型：WORD_QA、CLOZE_QUIZ、CLOZE_REVIEW',
  `wordbook_id` INT NOT NULL DEFAULT 0 COMMENT '生效词书 ID，0 表示全部词书',
  `name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  `system_prompt` MEDIUMTEXT NOT NULL COMMENT '系统提示词',
  `instruction_prompt` MEDIUMTEXT NOT NULL COMMENT '规则提示词',
  `output_schema_json` JSON NULL COMMENT '输出 JSON 结构',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用，0 否，1 是',
  `source_template_id` INT NULL COMMENT '复制来源模板 ID',
  `source_builtin_key` VARCHAR(64) NULL COMMENT '复制来源内置模板键',
  `created_by` INT NOT NULL COMMENT '创建人',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  KEY `idx_ai_prompt_template_feature` (`feature_type`, `wordbook_id`, `enabled`, `created_at`),
  KEY `idx_ai_prompt_template_source` (`source_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 提示词模板表';

CREATE TABLE IF NOT EXISTS `ai_prompt_feature_binding` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '绑定 ID',
  `feature_type` VARCHAR(64) NOT NULL COMMENT 'AI 功能类型：WORD_QA、CLOZE_QUIZ、CLOZE_REVIEW',
  `wordbook_id` INT NOT NULL DEFAULT 0 COMMENT '生效词书 ID，0 表示全部词书',
  `template_id` INT NULL COMMENT '当前使用的自定义模板 ID，空表示使用内置默认模板',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_binding_feature_wordbook` (`feature_type`, `wordbook_id`, `deleted`),
  KEY `idx_ai_prompt_binding_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 功能当前提示词绑定表';

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

CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` BIGINT NOT NULL COMMENT '日志 ID',
  `async_task_id` INT NULL COMMENT '关联异步任务 ID，非异步 AI 调用为空',
  `user_id` INT NULL COMMENT '调用用户，系统任务可为空',
  `config_scope` VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' COMMENT 'AI 配置来源：PUBLIC、PRIVATE',
  `content_type` VARCHAR(32) NOT NULL COMMENT 'AI 内容类型：WORD_QA、CLOZE、CLOZE_REVIEW、REPORT',
  `model_name` VARCHAR(128) NOT NULL COMMENT '模型名称',
  `api_base_url` VARCHAR(512) NULL COMMENT 'Base URL，不含 Key',
  `request_hash` VARCHAR(128) NULL COMMENT '请求摘要 hash',
  `prompt_feature_type` VARCHAR(64) NULL COMMENT '提示词功能类型：WORD_QA、CLOZE_QUIZ、CLOZE_REVIEW',
  `prompt_template_id` INT NULL COMMENT '提示词模板 ID，空表示内置默认模板或未接入提示词管理',
  `prompt_template_name` VARCHAR(128) NULL COMMENT '提示词模板名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'AI 调用状态：SUCCESS、FAILED',
  `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入 token',
  `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出 token',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总 token',
  `latency_ms` INT NOT NULL DEFAULT 0 COMMENT '调用耗时毫秒',
  `error_code` VARCHAR(128) NULL COMMENT '错误码',
  `error_message` VARCHAR(1024) NULL COMMENT '错误摘要',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_call_async_task` (`async_task_id`),
  KEY `idx_ai_call_user_time` (`user_id`, `created_at`),
  KEY `idx_ai_call_quota` (`user_id`, `config_scope`, `created_at`),
  KEY `idx_ai_call_type_time` (`content_type`, `created_at`),
  KEY `idx_ai_call_status` (`status`),
  KEY `idx_ai_call_model` (`model_name`),
  KEY `idx_ai_call_prompt_template` (`prompt_feature_type`, `prompt_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用日志表';

CREATE TABLE IF NOT EXISTS `async_task` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
  `user_id` INT NULL COMMENT '发起用户',
  `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型：AI_WORD_QA、AI_CLOZE、AI_CLOZE_REVIEW、AI_REPORT',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING、RUNNING、SUCCESS、FAILED',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '进度，0-100',
  `message` VARCHAR(512) NULL COMMENT '当前提示',
  `request_json` JSON NULL COMMENT '任务请求参数，去除敏感信息',
  `result_id` INT NULL COMMENT '结果 ID：AI_WORD_QA 对应 word_ai_qa.id，AI_CLOZE 对应 cloze_quiz.id，AI_CLOZE_REVIEW 对应 cloze_attempt_ai_review.id',
  `error_code` VARCHAR(128) NULL COMMENT '错误码',
  `error_message` VARCHAR(1024) NULL COMMENT '错误摘要',
  `started_at` DATETIME(3) NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_async_task_user_time` (`user_id`, `created_at`),
  KEY `idx_async_task_type_status` (`task_type`, `status`),
  KEY `idx_async_task_status_time` (`status`, `created_at`),
  KEY `idx_async_task_result` (`task_type`, `result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步任务表';

CREATE TABLE IF NOT EXISTS `cloze_quiz` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '题目 ID',
  `user_id` INT NOT NULL COMMENT '生成用户',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `daily_task_id` INT NULL COMMENT '来源今日任务 ID',
  `async_task_id` INT NULL COMMENT '来源异步任务 ID',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'MIXED' COMMENT '生成来源：TODAY_NEW、WRONG_WORDS、MIXED、COMPLETED_GROUP',
  `source_hash` CHAR(64) NULL COMMENT '生成输入上下文 hash',
  `cache_key` VARCHAR(255) NULL COMMENT '缓存 key，后端按完形上下文生成',
  `cache_active` TINYINT(1) NULL DEFAULT 1 COMMENT '当前可命中缓存，1 是，NULL 历史版本',
  `hit_count` INT NOT NULL DEFAULT 0 COMMENT '命中次数',
  `title` VARCHAR(255) NULL COMMENT '题目标题',
  `passage` MEDIUMTEXT NOT NULL COMMENT '短文内容，空格用占位符标记',
  `candidate_words` JSON NOT NULL COMMENT '候选词数组',
  `target_word_ids` JSON NOT NULL COMMENT '目标单词 ID 数组',
  `explanation` MEDIUMTEXT NULL COMMENT '短文中文翻译（passageZh）',
  `model_name` VARCHAR(128) NULL COMMENT '生成模型',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_cloze_quiz_user_time` (`user_id`, `created_at`),
  KEY `idx_cloze_quiz_wordbook` (`wordbook_id`),
  KEY `idx_cloze_quiz_task` (`async_task_id`),
  UNIQUE KEY `uk_cloze_quiz_active_cache` (`cache_key`, `cache_active`, `deleted`),
  KEY `idx_cloze_quiz_source` (`source_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 完形填空题目主表';

CREATE TABLE IF NOT EXISTS `cloze_quiz_blank` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '空格 ID',
  `quiz_id` INT NOT NULL COMMENT '题目 ID',
  `blank_no` INT NOT NULL COMMENT '空格序号，从 1 开始',
  `word_id` INT NOT NULL COMMENT '对应单词 ID',
  `answer_word` VARCHAR(128) NOT NULL COMMENT '标准答案',
  `hint` VARCHAR(255) NULL COMMENT '预留字段：当前不使用',
  `explanation` VARCHAR(1024) NULL COMMENT '单空解析 JSON：usedForm、usedPos、definitionZh、reasonZh',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cloze_blank_quiz_no` (`quiz_id`, `blank_no`, `deleted`),
  KEY `idx_cloze_blank_word` (`word_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='完形填空空格表';

CREATE TABLE IF NOT EXISTS `cloze_attempt` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '作答 ID',
  `quiz_id` INT NOT NULL COMMENT '题目 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `total_blanks` INT NOT NULL DEFAULT 0 COMMENT '总空格数',
  `correct_count` INT NOT NULL DEFAULT 0 COMMENT '正确数量',
  `wrong_count` INT NOT NULL DEFAULT 0 COMMENT '错误数量',
  `score` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '得分，0-100',
  `duration_seconds` INT NULL COMMENT '作答耗时',
  `submitted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_cloze_attempt_user_time` (`user_id`, `submitted_at`),
  KEY `idx_cloze_attempt_user_wordbook_time` (`user_id`, `wordbook_id`, `submitted_at`),
  KEY `idx_cloze_attempt_quiz` (`quiz_id`),
  KEY `idx_cloze_attempt_wordbook` (`wordbook_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='完形填空作答主表';

CREATE TABLE IF NOT EXISTS `cloze_attempt_answer` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '作答明细 ID',
  `attempt_id` INT NOT NULL COMMENT '作答 ID',
  `quiz_id` INT NOT NULL COMMENT '题目 ID',
  `blank_id` INT NOT NULL COMMENT '空格 ID',
  `word_id` INT NOT NULL COMMENT '标准答案对应单词 ID',
  `user_answer` VARCHAR(128) NULL COMMENT '用户答案',
  `correct_answer` VARCHAR(128) NOT NULL COMMENT '标准答案',
  `correct` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否正确，0 否，1 是',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cloze_answer_attempt_blank` (`attempt_id`, `blank_id`, `deleted`),
  KEY `idx_cloze_answer_word` (`word_id`),
  KEY `idx_cloze_answer_correct` (`attempt_id`, `correct`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='完形填空作答明细表';

CREATE TABLE IF NOT EXISTS `cloze_attempt_ai_review` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '评阅 ID',
  `attempt_id` INT NOT NULL COMMENT '作答 ID',
  `quiz_id` INT NOT NULL COMMENT '题目 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `wordbook_id` INT NOT NULL COMMENT '词库 ID',
  `source_hash` CHAR(64) NOT NULL COMMENT '评阅输入哈希',
  `content_json` JSON NULL COMMENT 'AI 评阅结构化内容',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '评阅状态：RUNNING、DONE、FAILED',
  `model_name` VARCHAR(128) NULL COMMENT '生成模型',
  `error_message` VARCHAR(1024) NULL COMMENT '错误信息',
  `started_at` DATETIME(3) NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) NULL COMMENT '结束时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cloze_attempt_ai_review_attempt` (`attempt_id`, `deleted`),
  KEY `idx_cloze_attempt_ai_review_user` (`user_id`, `created_at`),
  KEY `idx_cloze_attempt_ai_review_quiz` (`quiz_id`),
  KEY `idx_cloze_attempt_ai_review_wordbook` (`wordbook_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='完形填空 AI 评阅表';

CREATE TABLE IF NOT EXISTS `study_report` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '报告 ID',
  `user_id` INT NOT NULL COMMENT '用户 ID',
  `plan_id` INT NULL COMMENT '计划 ID',
  `wordbook_id` INT NULL COMMENT '词库 ID',
  `daily_task_id` INT NULL COMMENT '今日任务 ID',
  `async_task_id` INT NULL COMMENT '异步任务 ID',
  `report_date` DATE NOT NULL COMMENT '报告日期',
  `new_words_count` INT NOT NULL DEFAULT 0 COMMENT '当日新词数',
  `review_words_count` INT NOT NULL DEFAULT 0 COMMENT '当日复习词数',
  `quiz_accuracy` DECIMAL(5,2) NULL COMMENT '测验正确率',
  `wrong_word_ids` JSON NULL COMMENT '高频错误词 ID 数组',
  `summary_json` JSON NOT NULL COMMENT '结构化总结',
  `markdown_content` MEDIUMTEXT NOT NULL COMMENT 'Markdown 展示内容',
  `model_name` VARCHAR(128) NULL COMMENT '生成模型',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_report_user_date` (`user_id`, `report_date`, `deleted`),
  KEY `idx_study_report_task` (`daily_task_id`),
  KEY `idx_study_report_wordbook` (`wordbook_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 学习报告表';

CREATE TABLE IF NOT EXISTS `system_config` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '配置 ID',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键',
  `config_value` TEXT NULL COMMENT '配置值',
  `value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '配置值类型：STRING、NUMBER、BOOLEAN、JSON',
  `description` VARCHAR(512) NULL COMMENT '配置说明',
  `editable` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否后台可编辑，0 否，1 是',
  `created_by` INT NULL COMMENT '创建人',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除，1 已删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_config_key` (`config_key`, `deleted`),
  KEY `idx_system_config_editable` (`editable`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
