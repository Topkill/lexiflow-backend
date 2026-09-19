# LexiFlow 复习算法审查报告

更新日期：2026-09-19。实现基于 `feature/rabbitmq-word-import-async`。

## 本次落地范围

保留认识／不认识两档交互，替换原 q=2/4 驱动的 EF 计算，分开处理真实尝试、请求重试及每天的算法效果。既有 `quality_score=2/4` 继续作为历史统计标签，已不参与 EF 计算。

| 原问题 | 实现结果 |
| --- | --- |
| EF 只减不加 | 正式跨日成功可恢复 EF；限制 1.30～2.70 |
| 同日多入口失败反复扣 EF | 数据库每日记录按用户＋单词＋上海日期共享一次失败额度 |
| 即时补对增加正式次数 | IN_DAY_RETRY 不增加 EF、repetition，也不重排间隔 |
| 重复失败被整个任务项吞掉 | 新 attemptId 作为新尝试，记录事件并增加错误统计 |
| 网络重试导致重复处理 | 卡片复用 attemptId；完形以用户＋题目作为一次提交边界 |
| 并发读取后覆盖状态 | 同用户反馈短事务串行化，词状态读取同时使用行锁 |
| 间隔无上限与转换溢出 | BigDecimal HALF_UP 后限制 1～365，再 intValueExact |

此前 P1-1 的“复习先判断、失败后显示释义”保持有效。旧版评估及其数值表见 [历史评估](E:/lexiflow/lexiflow-backend/docs/复习算法说明与评估.md)，不再代表本次算法的参数。

## 反馈类型与服务端资格

卡片请求增加必填字段 `attemptId`（最长 64 字符）和 `attemptType`。前端通过 getRandomValues 为新一次选择生成 128 位随机标识，兼容局域网 HTTP 访问；失败响应或网络中断时保留完整请求，重试不生成新 ID；请求成功后才清除。已被另一设备完成的任务项不会因前端残留待确认请求而阻塞后续单词。

| 请求类型 | 使用场景 | 成功时的规则 |
| --- | --- | --- |
| INITIAL_LEARNING | 新词初学 | 仅从未学习的状态首次成功设 repetition=1、间隔1天；EF不增加 |
| FORMAL_REVIEW | 到期 REVIEW 项 | 需要后端核验跨日、到期、当日未失败且未成功推进，才增加 EF 和次数 |
| IN_DAY_RETRY | 当天失败后的补练、普通 EXTRA、独立错词专项 | 成功计数和事件保留，完成任务项，但不推进正式调度 |
| QUIZ | 完形填空 | 正确只记练习，失败共享每日惩罚额度 |

服务端按真实 `scene` 和当前词状态决定有效类型，不完全采用前端标签：

- 只有 REVIEW 项且请求 FORMAL_REVIEW，才有正式成功的资格。
- `lastStudiedAt` 的上海日期必须早于今天，且 `nextReviewDate <= today`。
- 当天已有 UNKNOWN 惩罚或已应用一次成功推进，则不能再次正式推进。
- NEW 或 EXTRA 伪装 FORMAL_REVIEW 会按补练处理；卡片伪装 QUIZ 会拒绝。
- 完形入口只接收 QUIZ，并由服务端固定事件类型为 QUIZ。
- 当天失败后再正确，不会恢复 EF；跨日回来只有满足上述正式复习条件才恢复。
- 已进入补练轮次的反馈始终标为 IN_DAY_RETRY，即使页面跨过午夜，也不能将看过释义后的答对升级为正式复习；恢复正式复习轮次时，昨天的释义页会回到回忆页。旧缓存不会作为后端额度依据。

`INITIAL_LEARNING`、`QUIZ` 或 `IN_DAY_RETRY` 的成功，不会被当作正式复习成功。初学失败后补对保持 repetition=0，下一次正式复习成功才变为1。

按照本次计划“所有真实 UNKNOWN 共享相同规则”，**独立错词专项及其完形的失败也会进入每日失败额度**；它们的成功仍不恢复 EF。这一点区别于原先专项任务完全跳过 SM-2。

## 数据与并发

新增 `study_daily_word_effect`，唯一键 `(user_id, word_id, business_date)`。除计划中的 `unknown_ef_applied`，补充一个 `known_review_applied`，用于限制同日跨多个任务项的成功推进。

每日行通过 `INSERT ... ON DUPLICATE KEY UPDATE` 创建／复用，再 `FOR UPDATE` 读取。**记录存在不代表当天已经惩罚**，以 `unknown_ef_applied` 标记为准：正确作答也可能已创建该日记录。

`study_event` 新增：

- `attempt_id`：真实尝试标识，唯一键 `(user_id, attempt_id)`；老记录保留 NULL。
- `attempt_type`：服务端判定的有效练习类型。
- `business_date`：服务端按 Asia/Shanghai 计算。
- `algorithm_applied`：本次是否应用 EF／正式次数／间隔规则。后续失败仍可能更新困难标签、计数和时间戳，该字段仍为 false。

卡片失败的新 attemptId 会再次更新 `wrong_count`、`wrong_word` 并新增事件；同一 attemptId 的请求重放不重复写入。已完成的任务项不会重新开放，新的练习须进入未完成任务项。

完形每篇只有一次正式提交：重复提交返回已有作答，不再次写事件。每空事件使用 `quiz:题目ID:空格ID` 标识。重新生成的新篇章属于新尝试，但仍共享当天的单词惩罚额度。

### 锁与事务选择

卡片和完形提交在开始读取业务状态前锁定当前 `users` 行；用户之间互不等待，同一用户的不同设备和入口按短事务依次处理。事务内完成每日额度、词状态、事件、错词和任务更新，失败整体回滚。首次创建词状态也受同一个锁保护，不会先同时判断不存在再竞争插入。

使用用户粒度是为了以较少代码同时覆盖多词完形提交、首次状态创建和共享任务／计划计数，避免多种锁的排序与重试。AI 生成在提交后触发，不把模型调用放进持锁事务；本次没有增加 Redis 锁、消息队列或新的缓存层。

日记录唯一键不是请求幂等，任务项状态也不是每天失败额度：三者分别解决不同问题。

### 跨词库口径

每日额度严格不含 wordbookId，同用户同 wordId 跨词库也共享额度。现有 `user_word_state` 仍按用户＋词库＋单词保存；本次不合并各词库历史记忆状态。当天首次失败改变该次反馈所属词库状态，其他词库后续失败只记录各自的错误统计，不再扣 EF 或重置已有间隔。首次建立某词库状态时默认等待明日复习。

因此“每天最多一次惩罚”不等于“跨词库的 EF 永远相同”。如后续要求完全共享记忆，需要另行迁移词状态，不能仅删除唯一键中的词库字段。

## 升级方式

已有库先执行 [20260919_binary_review_daily_effect.sql](E:/lexiflow/lexiflow-backend/docs/sql/migrations/20260919_binary_review_daily_effect.sql)，再部署配套后端与前端；新库使用更新后的 `schema.sql`，不再执行同一迁移。

本次未连接业务数据库执行迁移。新增事件字段允许旧数据为空；不猜测或回填历史算法效果，日额度从升级后首次反馈开始计算。升级当天旧算法已经产生的惩罚不会自动折算为新额度。

前后端接口需同时升级；仍缺少 attemptId／attemptType 的旧卡片请求会被参数校验拒绝。部署后应刷新旧页面。

## 验证与仍未覆盖的事项

按照用户要求，本次不新增测试用例。仅同步已有测试对接口签名的调用和浏览器 crypto mock，以维持现有代码可编译；不宣称进行了并发压测、数据库迁移验证或浏览器联调。

构建使用指定的 JDK 17：

```powershell
$env:JAVA_HOME="C:\Users\legion\.jdks\oracle_open_jdk-17"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn clean package -DskipTests
```

前端执行 `npm run build`。两个构建均通过；Maven 会编译已有测试源码，但跳过测试执行。

本次未调整“已掌握／困难”标签阈值、完形填空导致的计划掌握冗余计数漂移、必做完形对任务入口的限制，也未消除普通任务的同词 REVIEW/EXTRA 展示重复。其同日重复算法推进已被后端限制，但展示是否去重属于另外的产品修改。

## 改进建议与最终规则

短期先观察每日复习量、首次回忆正确率和 EF 分布，不立即再引入复杂记忆模型。若同账号高并发实测出现用户行锁等待，再考虑缩小为用户＋单词锁，并统一多词加锁顺序。

```text
初始 EF = 2.50，EF 范围 [1.30, 2.70]
业务日期 = 服务端 Asia/Shanghai 自然日

第一次真实 UNKNOWN / 用户 / 单词 / 日期：
    EF = max(1.30, EF - 0.20)
    repetition = 0
    interval = 1，next_review_date = 今天 + 1
    wrong_count + 1，错词更新，事件记录，algorithm_applied = true

同日后续真实 UNKNOWN：
    不改 EF、repetition、已有 interval / next_review_date
    wrong_count + 1，错词更新，事件记录，algorithm_applied = false

初学首次 KNOWN（此前从未学习且当日未失败）：
    repetition = 1，interval = 1，EF 不变

日内补练 / EXTRA / 专项 / QUIZ 的 KNOWN：
    correct_count + 1，事件记录
    卡片完成任务；不增加 EF、正式 repetition，不重排间隔

通过服务端资格检查的跨日正式 KNOWN（每天最多一次）：
    EF = min(2.70, EF + 0.05)
    repetition += 1
    repetition = 1 时 interval = 1
    repetition = 2 时 interval = 3
    repetition >= 3 时 interval = clamp(HALF_UP(previousInterval × 新EF), 1, 365)
    限制范围后再转换 int；next_review_date = 今天 + interval

同一个 attemptId 重放 / 同一完形题重提：
    不重复事件、计数或算法效果
```
