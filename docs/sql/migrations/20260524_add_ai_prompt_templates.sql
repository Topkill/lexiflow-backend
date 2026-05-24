CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '提示词模板 ID',
  `feature_type` VARCHAR(64) NOT NULL COMMENT 'AI 功能类型：WORD_QA、CLOZE_QUIZ、CLOZE_REVIEW',
  `name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  `system_prompt` MEDIUMTEXT NOT NULL COMMENT '系统提示词',
  `instruction_prompt` MEDIUMTEXT NOT NULL COMMENT '规则提示词',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `source_template_id` INT NULL COMMENT '复制来源模板 ID',
  `source_builtin_key` VARCHAR(64) NULL COMMENT '复制来源内置模板键',
  `created_by` INT NOT NULL COMMENT '创建人',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  KEY `idx_ai_prompt_template_feature` (`feature_type`, `enabled`, `created_at`),
  KEY `idx_ai_prompt_template_source` (`source_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 提示词模板表';

CREATE TABLE IF NOT EXISTS `ai_prompt_feature_binding` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '绑定 ID',
  `feature_type` VARCHAR(64) NOT NULL COMMENT 'AI 功能类型',
  `template_id` INT NULL COMMENT '当前使用的自定义模板 ID，空表示使用内置默认模板',
  `updated_by` INT NULL COMMENT '更新人',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_binding_feature` (`feature_type`, `deleted`),
  KEY `idx_ai_prompt_binding_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 功能当前提示词绑定表';

ALTER TABLE `ai_call_log`
  ADD COLUMN `prompt_feature_type` VARCHAR(64) NULL COMMENT '提示词功能类型' AFTER `request_hash`,
  ADD COLUMN `prompt_template_id` INT NULL COMMENT '提示词模板 ID，空表示内置默认模板或未接入提示词管理' AFTER `prompt_feature_type`,
  ADD COLUMN `prompt_template_name` VARCHAR(128) NULL COMMENT '提示词模板名称' AFTER `prompt_template_id`,
  ADD KEY `idx_ai_call_prompt_template` (`prompt_feature_type`, `prompt_template_id`);
