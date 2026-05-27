-- 用于在旧版 AI 提示词表基础上补充词书范围。
-- 如果新库按 docs/sql/migrations 顺序建库，本迁移应在 20260524 后执行。
-- 如果新库直接导入 docs/sql/schema.sql 全量结构，不要再重复执行本迁移。

ALTER TABLE `ai_prompt_template`
  ADD COLUMN `wordbook_id` INT NOT NULL DEFAULT 0 COMMENT '生效词书 ID，0 表示全部词书' AFTER `feature_type`,
  DROP INDEX `idx_ai_prompt_template_feature`,
  ADD KEY `idx_ai_prompt_template_feature` (`feature_type`, `wordbook_id`, `enabled`, `created_at`);

ALTER TABLE `ai_prompt_feature_binding`
  ADD COLUMN `wordbook_id` INT NOT NULL DEFAULT 0 COMMENT '生效词书 ID，0 表示全部词书' AFTER `feature_type`,
  DROP INDEX `uk_ai_prompt_binding_feature`,
  ADD UNIQUE KEY `uk_ai_prompt_binding_feature_wordbook` (`feature_type`, `wordbook_id`, `deleted`);
