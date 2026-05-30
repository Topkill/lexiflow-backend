-- 更新完形填空空格解析字段注释，补充 usedForm。
-- 推荐执行方式：
-- Get-Content -Path docs\sql\migrations\20260530_update_cloze_blank_explanation_comment.sql -Raw -Encoding UTF8 | mysql --default-character-set=utf8mb4 -uroot -proot lexiflow

ALTER TABLE `cloze_quiz_blank`
  MODIFY COLUMN `explanation` VARCHAR(1024) NULL COMMENT '单空解析 JSON：usedForm、usedPos、definitionZh、reasonZh';
