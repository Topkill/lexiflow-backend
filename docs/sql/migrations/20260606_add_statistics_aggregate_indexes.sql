ALTER TABLE `study_event`
  ADD KEY `idx_study_event_time_user` (`created_at`, `user_id`),
  ADD KEY `idx_study_event_user_wordbook_time_correct` (`user_id`, `wordbook_id`, `created_at`, `is_correct`);

ALTER TABLE `cloze_attempt`
  ADD KEY `idx_cloze_attempt_user_wordbook_time` (`user_id`, `wordbook_id`, `submitted_at`);
