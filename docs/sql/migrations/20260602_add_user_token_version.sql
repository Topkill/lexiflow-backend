ALTER TABLE `users`
  ADD COLUMN `token_version` INT NOT NULL DEFAULT 1 COMMENT '用户级 Token 版本，递增后旧 token 失效' AFTER `status`;
