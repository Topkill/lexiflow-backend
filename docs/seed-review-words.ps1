<#
.SYNOPSIS
为测试账号快速制造今天到期的复习词。

.DESCRIPTION
按邮箱找到测试用户和主学习计划，把一批词写入 user_word_state，并设置为 learned=1、
next_review_date=CURDATE()。刷新 /app/study 后，后端会把这些词同步为 REVIEW 任务项。

示例：
  pwsh .\docs\seed-review-words.ps1 -Email "e2e_choice_20260521@example.test"
  pwsh .\docs\seed-review-words.ps1 -Email "e2e_choice_20260521@example.test" -ReviewCount 10

注意：
  1. 仅用于本地测试账号。
  2. 需要本机可执行 mysql 客户端。
  3. 如果当前已经有未完成 DAILY 任务，脚本会避开这个任务里已有的词，防止被后端排除。
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Email,

    [ValidateRange(1, 200)]
    [int] $ReviewCount = 5,

    [ValidateRange(1, 200)]
    [int] $NewWordsPerGroup = 5,

    [ValidateRange(1, 200)]
    [int] $ReviewWordsPerGroup = $ReviewCount,

    [string] $HostName = "localhost",
    [int] $Port = 3306,
    [string] $Database = "lexiflow",
    [string] $DbUser = "root",
    [string] $Password = "root"
)

$ErrorActionPreference = "Stop"

function Invoke-MysqlRows {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql
    )

    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysql) {
        throw "找不到 mysql 客户端，请先把 MySQL bin 目录加入 PATH。"
    }

    $oldMysqlPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $output = $Sql | & $mysql.Source `
            --host=$HostName `
            --port=$Port `
            --user=$DbUser `
            --default-character-set=utf8mb4 `
            --batch `
            --raw `
            --skip-column-names `
            $Database

        if ($LASTEXITCODE -ne 0) {
            throw "mysql 执行失败，退出码：$LASTEXITCODE"
        }

        if ($null -eq $output) {
            return @()
        }
        return @($output)
    } finally {
        $env:MYSQL_PWD = $oldMysqlPwd
    }
}

function Escape-SqlLiteral {
    param([string] $Value)
    return $Value.Replace("\", "\\").Replace("'", "''")
}

$emailLiteral = Escape-SqlLiteral $Email.Trim().ToLowerInvariant()

$userRows = Invoke-MysqlRows "SELECT id FROM users WHERE email = '$emailLiteral' AND deleted = 0 LIMIT 1;"
if ($userRows.Count -eq 0 -or [string]::IsNullOrWhiteSpace($userRows[0])) {
    throw "没有找到邮箱为 $Email 的用户，请先注册并登录一次。"
}
$userId = [long] $userRows[0]

$planRows = Invoke-MysqlRows @"
SELECT id, wordbook_id
FROM study_plan
WHERE user_id = $userId
  AND status = 'ACTIVE'
  AND is_primary = 1
  AND deleted = 0
ORDER BY id DESC
LIMIT 1;
"@
if ($planRows.Count -eq 0 -or [string]::IsNullOrWhiteSpace($planRows[0])) {
    throw "用户 $Email 还没有主学习计划，请先在前端创建学习计划。"
}

$planParts = $planRows[0] -split "`t"
$planId = [long] $planParts[0]
$wordbookId = [long] $planParts[1]

$seedSql = @"
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_existing_daily_words;
CREATE TEMPORARY TABLE tmp_existing_daily_words AS
SELECT DISTINCT dti.word_id
FROM daily_task dt
JOIN daily_task_item dti ON dti.daily_task_id = dt.id
WHERE dt.user_id = $userId
  AND dt.plan_id = $planId
  AND dt.task_type = 'DAILY'
  AND dt.status = 'PENDING'
  AND dt.deleted = 0
  AND dti.deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_review_words;
CREATE TEMPORARY TABLE tmp_review_words AS
SELECT w.id AS word_id, w.sequence_no
FROM word w
LEFT JOIN tmp_existing_daily_words existing ON existing.word_id = w.id
WHERE w.wordbook_id = $wordbookId
  AND w.enabled = 1
  AND w.deleted = 0
  AND existing.word_id IS NULL
  AND (w.primary_definition IS NOT NULL OR w.trans IS NOT NULL)
ORDER BY w.sequence_no ASC, w.id ASC
LIMIT $ReviewCount;

INSERT INTO user_word_state (
  user_id,
  wordbook_id,
  word_id,
  plan_id,
  mastery_status,
  learned,
  repetition,
  interval_days,
  easiness_factor,
  next_review_date,
  last_feedback,
  last_studied_at,
  last_reviewed_at,
  wrong_count,
  correct_count,
  deleted
)
SELECT
  $userId,
  $wordbookId,
  word_id,
  $planId,
  'LEARNING',
  1,
  1,
  1,
  2.50,
  CURDATE(),
  'KNOWN',
  NOW(3),
  DATE_SUB(NOW(3), INTERVAL 1 DAY),
  0,
  1,
  0
FROM tmp_review_words
ON DUPLICATE KEY UPDATE
  plan_id = $planId,
  mastery_status = 'LEARNING',
  learned = 1,
  repetition = 1,
  interval_days = 1,
  easiness_factor = 2.50,
  next_review_date = CURDATE(),
  last_feedback = 'KNOWN',
  last_studied_at = NOW(3),
  last_reviewed_at = DATE_SUB(NOW(3), INTERVAL 1 DAY),
  correct_count = GREATEST(correct_count, 1),
  deleted = 0;

UPDATE study_plan sp
JOIN (
  SELECT COALESCE(MAX(sequence_no), 0) AS max_sequence_no,
         COUNT(*) AS seeded_count
  FROM tmp_review_words
) seeded
SET sp.current_sequence_no = GREATEST(sp.current_sequence_no, seeded.max_sequence_no),
    sp.learned_count = GREATEST(sp.learned_count, seeded.seeded_count),
    sp.new_words_per_group = $NewWordsPerGroup,
    sp.review_words_per_group = $ReviewWordsPerGroup
WHERE sp.id = $planId;

SELECT COUNT(*) FROM tmp_review_words;
SELECT GROUP_CONCAT(word_id ORDER BY sequence_no ASC SEPARATOR ',') FROM tmp_review_words;
"@

$resultRows = Invoke-MysqlRows $seedSql
$seededCount = if ($resultRows.Count -ge 1 -and $resultRows[0]) { [int] $resultRows[0] } else { 0 }
$seededWordIds = if ($resultRows.Count -ge 2 -and $resultRows[1]) { $resultRows[1] } else { "" }

Write-Host "已为测试账号制造复习词："
Write-Host "  邮箱：$Email"
Write-Host "  user_id：$userId"
Write-Host "  plan_id：$planId"
Write-Host "  wordbook_id：$wordbookId"
Write-Host "  复习词数量：$seededCount / $ReviewCount"
if ($seededWordIds) {
    Write-Host "  word_id：$seededWordIds"
}
Write-Host ""
Write-Host "下一步：刷新 /app/study；如果已在学习页，刷新后端会把这些词同步进 REVIEW 流程。"
