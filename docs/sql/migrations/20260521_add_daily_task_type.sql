ALTER TABLE `daily_task`
  ADD COLUMN `task_type` VARCHAR(32) NOT NULL DEFAULT 'DAILY' COMMENT '任务类型：DAILY、WRONG_WORD_PRACTICE' AFTER `group_no`;

ALTER TABLE `daily_task`
  DROP INDEX `uk_daily_task_user_plan_date_group`,
  ADD UNIQUE KEY `uk_daily_task_user_plan_date_group_type` (`user_id`, `plan_id`, `task_date`, `group_no`, `task_type`, `deleted`),
  ADD KEY `idx_daily_task_user_type_date` (`user_id`, `task_type`, `task_date`);
