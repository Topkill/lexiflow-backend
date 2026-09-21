-- 升级既有数据库时执行一次；新库使用 schema.sql，不重复执行此迁移。
-- 一道完形一题一次：在数据库层兜底（用户行锁串行化之外的最终防线），
-- 防止任何写入路径（脚本导入、未来新增入口、绕过锁的直写）产生重复作答记录。
ALTER TABLE cloze_attempt
  ADD UNIQUE KEY uk_cloze_attempt_user_quiz (user_id, quiz_id);