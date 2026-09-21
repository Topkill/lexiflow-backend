-- 升级既有数据库时执行一次；新库使用 schema.sql，不重复执行此迁移。
-- 为算法效果评估补充调度前后快照：新事件由应用写入版本号与前后状态，
-- 存量事件保持 NULL（不猜测历史算法版本与效果）。
ALTER TABLE study_event
  ADD COLUMN algorithm_version VARCHAR(32) NULL COMMENT '算法版本号，历史事件为 NULL，新事件由应用写入',
  ADD COLUMN ef_before DECIMAL(3, 2) NULL COMMENT '变更前 EF',
  ADD COLUMN interval_days_before INT NULL COMMENT '变更前间隔(天)',
  ADD COLUMN repetition_before INT NULL COMMENT '变更前连续成功次数',
  ADD COLUMN status_before VARCHAR(20) NULL COMMENT '变更前掌握状态',
  ADD COLUMN ef_after DECIMAL(3, 2) NULL COMMENT '变更后 EF',
  ADD COLUMN interval_days_after INT NULL COMMENT '变更后间隔(天)',
  ADD COLUMN repetition_after INT NULL COMMENT '变更后连续成功次数',
  ADD COLUMN status_after VARCHAR(20) NULL COMMENT '变更后掌握状态',
  ADD COLUMN next_review_date_after DATE NULL COMMENT '计算出的下次复习日期';