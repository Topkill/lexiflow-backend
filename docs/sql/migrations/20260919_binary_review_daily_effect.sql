-- 升级既有数据库时执行一次；新库使用 schema.sql，不重复执行此迁移。
CREATE TABLE study_daily_word_effect (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id INT NOT NULL,
  word_id INT NOT NULL,
  business_date DATE NOT NULL,
  unknown_ef_applied TINYINT(1) NOT NULL DEFAULT 0,
  known_review_applied TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_word_effect (user_id, word_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE study_event
  ADD COLUMN attempt_id VARCHAR(64) NULL COMMENT '真实尝试 ID，网络重试复用',
  ADD COLUMN attempt_type VARCHAR(32) NULL COMMENT '服务端判定的练习类型',
  ADD COLUMN business_date DATE NULL COMMENT 'Asia/Shanghai 业务日期',
  ADD COLUMN algorithm_applied TINYINT(1) NULL COMMENT '是否应用 EF/间隔规则，NULL 为历史未知',
  ADD UNIQUE KEY uk_study_event_attempt (user_id, attempt_id);

-- 老事件保留未知类型和算法标记，不猜测历史算法效果。
-- 不回填当天额度：上线日从升级后的首次反馈开始计数。
