ALTER TABLE `ai_call_log`
  ADD COLUMN `async_task_id` INT NULL COMMENT '关联异步任务 ID，非异步 AI 调用为空' AFTER `id`,
  ADD KEY `idx_ai_call_async_task` (`async_task_id`);
