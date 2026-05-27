-- 修复 AI 提示词相关表注释乱码。
-- 如果数据库工具里看到 COMMENT 乱码，通常是执行建表/迁移时客户端字符集不对。
-- Windows PowerShell 执行时建议使用：
-- Get-Content -Path docs\sql\migrations\20260527_fix_ai_prompt_template_comments.sql -Raw -Encoding UTF8 | mysql --default-character-set=utf8mb4 -uroot -proot lexiflow

ALTER TABLE `ai_prompt_template`
  COMMENT = 'AI 提示词模板表',
  MODIFY COLUMN `id` INT NOT NULL AUTO_INCREMENT COMMENT '提示词模板 ID',
  MODIFY COLUMN `feature_type` VARCHAR(64) NOT NULL COMMENT 'AI 功能类型：WORD_QA、CLOZE_QUIZ、CLOZE_REVIEW',
  MODIFY COLUMN `wordbook_id` INT NOT NULL DEFAULT 0 COMMENT '生效词书 ID，0 表示全部词书',
  MODIFY COLUMN `name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `system_prompt` MEDIUMTEXT NOT NULL COMMENT '系统提示词',
  MODIFY COLUMN `instruction_prompt` MEDIUMTEXT NOT NULL COMMENT '规则提示词',
  MODIFY COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  MODIFY COLUMN `source_template_id` INT NULL COMMENT '复制来源模板 ID',
  MODIFY COLUMN `source_builtin_key` VARCHAR(64) NULL COMMENT '复制来源内置模板键',
  MODIFY COLUMN `created_by` INT NOT NULL COMMENT '创建人',
  MODIFY COLUMN `updated_by` INT NULL COMMENT '更新人',
  MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  MODIFY COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁';

ALTER TABLE `ai_prompt_feature_binding`
  COMMENT = 'AI 功能当前提示词绑定表',
  MODIFY COLUMN `id` INT NOT NULL AUTO_INCREMENT COMMENT '绑定 ID',
  MODIFY COLUMN `feature_type` VARCHAR(64) NOT NULL COMMENT 'AI 功能类型',
  MODIFY COLUMN `wordbook_id` INT NOT NULL DEFAULT 0 COMMENT '生效词书 ID，0 表示全部词书',
  MODIFY COLUMN `template_id` INT NULL COMMENT '当前使用的自定义模板 ID，空表示使用内置默认模板',
  MODIFY COLUMN `updated_by` INT NULL COMMENT '更新人',
  MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  MODIFY COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除';
