-- 为 AI 提示词模板补充可配置输出 JSON 结构。
-- 如果新库直接导入 docs/sql/schema.sql 全量结构，不要再重复执行本迁移。

ALTER TABLE `ai_prompt_template`
  ADD COLUMN `output_schema_json` JSON NULL COMMENT '输出 JSON 结构' AFTER `instruction_prompt`;
