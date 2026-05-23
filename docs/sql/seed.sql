-- LexiFlow seed data
-- Run after docs/sql/schema.sql
-- Password note: default admin is admin@qq.com / 12345aaa for local development only.
-- AI key note: public AI config uses a placeholder encrypted key. Do not store a real API key in seed.sql.

USE `lexiflow`;

SET NAMES utf8mb4;

-- Default admin user
INSERT INTO `users` (
  `id`, `email`, `password_hash`, `nickname`, `avatar_url`, `role`, `status`,
  `created_at`, `updated_at`, `deleted`
) VALUES (
  1,
  'admin@qq.com',
  '$2a$10$LS3UyyhxhURG24MKbUXnRuvf/4YtBAxt1y1p10RxxsxQ00d.n9pUC',
  'LexiFlow 管理员',
  NULL,
  'ADMIN',
  'ACTIVE',
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3),
  0
) ON DUPLICATE KEY UPDATE
  `nickname` = VALUES(`nickname`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`),
  `updated_at` = CURRENT_TIMESTAMP(3);

INSERT INTO `user_settings` (
  `id`, `user_id`, `target_exam`, `daily_new_words`, `ai_key_mode`, `enable_daily_report`, `timezone`,
  `created_at`, `updated_at`, `deleted`
) VALUES (
  1,
  1,
  'CET4',
  30,
  'PUBLIC',
  1,
  'Asia/Shanghai',
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3),
  0
) ON DUPLICATE KEY UPDATE
  `target_exam` = VALUES(`target_exam`),
  `daily_new_words` = VALUES(`daily_new_words`),
  `ai_key_mode` = VALUES(`ai_key_mode`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- System configuration
INSERT INTO `system_config` (
  `id`, `config_key`, `config_value`, `value_type`, `description`, `editable`,
  `created_by`, `updated_by`, `created_at`, `updated_at`, `deleted`, `version`
) VALUES
  (1, 'app.name', 'LexiFlow', 'STRING', '应用名称', 0, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0, 0),
  (2, 'study.default_daily_new_words', '30', 'NUMBER', '默认每日新词数量', 1, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0, 0),
  (3, 'ai.public_daily_quota', '20', 'NUMBER', '公共 AI 每用户每日调用配额', 1, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0, 0),
  (4, 'upload.excel_max_rows', '5000', 'NUMBER', 'Excel 导入最大行数', 1, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0, 0),
  (5, 'upload.excel_max_size_mb', '10', 'NUMBER', 'Excel 导入最大文件大小 MB', 1, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0, 0)
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `description` = VALUES(`description`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- Public AI config placeholder
INSERT INTO `ai_public_config` (
  `id`, `name`, `api_base_url`, `encrypted_api_key`, `model_name`, `temperature`,
  `stream_enabled`, `daily_quota_per_user`, `active`, `enabled`, `remark`, `created_by`, `updated_by`,
  `created_at`, `updated_at`, `deleted`, `version`
) VALUES (
  1,
  'OpenAI Compatible Placeholder',
  'https://api.example.com/v1',
  'PLACEHOLDER_ENCRYPTED_API_KEY_DO_NOT_USE',
  'gpt-4.1-mini',
  0.70,
  0,
  20,
  0,
  0,
  '占位配置：请在后台配置真实 Base URL、模型和加密后的 API Key 后再启用。',
  1,
  NULL,
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3),
  0,
  0
) ON DUPLICATE KEY UPDATE
  `api_base_url` = VALUES(`api_base_url`),
  `model_name` = VALUES(`model_name`),
  `stream_enabled` = VALUES(`stream_enabled`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- Built-in wordbooks
INSERT INTO `wordbook` (
  `id`, `name`, `code`, `type`, `description`, `cover_url`, `difficulty_level`, `word_count`,
  `enabled`, `sort_order`, `created_by`, `updated_by`, `created_at`, `updated_at`, `deleted`
) VALUES
  (1, 'CET4 核心词', 'CET4_CORE', 'CET4', '大学英语四级核心词库，适合四级备考入门与基础巩固。', NULL, 2, 0, 1, 10, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (2, 'CET6 核心词', 'CET6_CORE', 'CET6', '大学英语六级核心词库，适合六级阅读、听力和写作积累。', NULL, 3, 0, 1, 20, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0),
  (3, '考研英语核心词', 'POSTGRADUATE_CORE', 'POSTGRADUATE', '考研英语核心词库，强调高频词义、熟词僻义和阅读理解场景。', NULL, 4, 0, 1, 30, 1, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `word_count` = VALUES(`word_count`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = CURRENT_TIMESTAMP(3);

-- Word data is imported through the admin JSON URL import endpoint.

