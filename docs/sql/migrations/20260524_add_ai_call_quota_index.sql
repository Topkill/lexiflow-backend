ALTER TABLE `ai_call_log`
  ADD KEY `idx_ai_call_quota` (`user_id`, `config_scope`, `created_at`);
