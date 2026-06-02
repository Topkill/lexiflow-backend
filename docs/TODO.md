# 待办事项

## 已完成：长任务 MQ 异步化与轮询恢复

- 词库 Excel / JSON URL 导入已改为 RabbitMQ 异步任务，管理端通过任务状态轮询查看进度。
- AI 完形填空生成已改为 RabbitMQ 异步任务，前端持久化 `taskId` 并通过 `GET /api/v1/tasks/{taskId}` 轮询恢复生成中状态。
- 完形填空同一用户、同一学习组、同一生成来源、同一目标词数的 `PENDING/RUNNING` 任务会直接复用，避免刷新、多标签或多设备重复触发 AI。
- AI 学习报告生成已改为 RabbitMQ 异步任务，前端生成报告后轮询任务状态，成功后自动打开报告。

### 仍需关注

- 任务 SSE 仍未实现；当前选择 REST 轮询作为第一版任务状态方案。
- AI 评阅刷新恢复仍是独立事项，和完形填空生成任务不是同一条链路。
- `docs/seed-review-words.ps1` 仍然可用于制造复习词，方便配合异步任务测试。

## 多设备登录与会话管理

- 当前状态：认证采用无状态 JWT，同一用户可以在多台设备或多个浏览器同时登录。
- MVP 阶段：暂不限制多设备登录，也暂不增加会话表、设备管理、踢下线或“退出全部设备”。
- 后续触发条件：如果出现账号共享限制、安全退出全部设备、登录设备管理、管理员强制下线等需求，再补会话管理。

### 可选改造方向

1. 增加用户登录设备/会话表，支持查看和单独踢下线。
2. 增加 `tokenVersion` 或 session version，新登录时让旧 token 失效，实现单设备登录。
3. 按设备类型限制登录，例如 Web 端保留一个，移动端保留一个。

## 修改密码后踢出所有设备

- 当前状态：项目暂未实现修改密码后的全设备下线机制。
- 目标：用户修改密码成功后，该用户所有旧设备上的 access token 和 refresh token 都应失效。
- 推荐方案：使用用户级 `token_version`，而不是只删除当前浏览器 Cookie。

### 建议改造

1. `user` 表增加 `token_version` 字段，默认 `0`。
2. JWT 签发时写入当前用户的 `token_version`。
3. JWT 鉴权时校验 token 内版本是否等于数据库当前版本。
4. 修改密码成功后执行 `token_version = token_version + 1`。
5. 旧设备再次请求或刷新 token 时返回未登录，前端跳转登录页。

### 备注

- 只清除当前设备的 `refresh_token` Cookie 不够，其他设备仍持有自己的 token。
- 也可以用 `password_changed_at` + JWT `iat` 实现，但 `token_version` 更简单，后续也可复用到“退出全部设备”和“管理员强制下线”。

## AI 公共额度查询优化

- 当前状态：公共 AI 额度仍然按 `ai_call_log` 当天记录数统计。
- 已有索引：`ai_call_log(user_id, created_at)`。
- 本次补充：增加 `ai_call_log(user_id, config_scope, created_at)` 复合索引，减少额度查询的条件扫描。
- 当前结论：先保持日志表计数，不改成独立额度表。
- 后续方向：如果日志量和并发明显上涨，再考虑 `ai_quota_usage` 每日额度表或 Redis 计数。

## AI 问答流式失败兜底成本优化

- 当前状态：AI 问答流式失败后，前端会在已有部分回答时调用一次普通问答接口尝试补全结果。
- 当前收益：尽量保证用户最终能看到完整 AI 回答，减少“回答到一半失败”的挫败感。
- 当前代价：如果流式调用已经消耗了 AI token，但失败发生在缓存写入前，补全请求会再次调用 AI，可能多消耗一次公共额度或用户私有 Key 成本。
- 当前结论：先保持现状，优先保证结果完整性，不立即改成 cache-only。

### 后续触发条件

1. AI 调用日志显示同一用户、同一问题短时间内重复调用明显增多。
2. 公共额度消耗过快，且和流式失败重试有关。
3. 用户私有 Key 场景出现成本敏感反馈。
4. 需要更严格地区分“补缓存”和“重新生成”。

### 可选改造方向

1. 增加 cache-only 补拉接口：只查缓存，不命中时不调用 AI。
2. 给 AI 调用日志增加 fallback/retry 标记，方便统计重复调用成本。
3. 流式失败后只保留部分回答并提示用户手动重试。
4. 后端统一兜底生成，前端不主动发第二次普通生成请求。

## 同 cacheKey 并发 AI 调用合并

- 当前状态：AI 内容缓存表有 `uk_ai_cache_key(content_type, cache_key, deleted)` 唯一索引，可以保证并发写入时不会产生重复缓存记录。
- 当前处理：如果两个相同 `cacheKey` 的请求同时未命中缓存，它们仍可能各自调用一次 AI；后写入缓存的一方如果撞到唯一键，只需要吞掉 `DuplicateKeyException`，避免请求失败。
- 当前代价：数据库不会重复，但 AI 调用可能重复，极端情况下会多消耗一次公共额度或用户私有 Key 成本。
- 当前结论：短期先不做同 key 请求合并，保持唯一索引兜底和异常处理即可。

### 后续触发条件

1. AI 调用日志显示同一 `contentType + cacheKey` 在短时间内重复调用明显增多。
2. 公共 AI 额度或用户私有 Key 成本开始受并发重复调用影响。
3. 前端双击、刷新重试、多个页面同时打开等场景频繁触发同一个 AI 请求。
4. 后端部署为多实例后，需要更明确的跨实例并发控制。

### 可选改造方向

1. 单实例阶段：用本地 `ConcurrentHashMap<cacheKey, CompletableFuture<?>>` 合并同 key in-flight 请求。
2. 多实例阶段：用 Redis 分布式锁，锁持有者调用 AI，等待者轮询缓存或短暂等待。
3. 数据库方案：增加 in-flight 状态表或缓存状态字段，区分 `RUNNING`、`DONE`、`FAILED`。
4. 前端辅助：对同一个按钮增加防重复点击和请求中禁用，减少低成本重复触发。

## AI 输出结构字段展示元数据

- 当前状态：`outputSchema` 是示例 JSON 对象，能让前端提前知道字段名和大致类型，但不包含中文标题、展示顺序、展示组件等 UI 元数据。
- 当前收益：结构简单，和 AI 实际输出 JSON 保持一致，适合先解决“扩展字段能展示”的问题。
- 当前限制：扩展字段在前端默认使用字段名作为标题，不能由管理员配置中文标题。
- 当前结论：先保持示例 JSON 结构，不引入完整 JSON Schema 或字段元数据 DSL。

### 后续触发条件

1. 管理员需要为扩展字段配置中文标题、排序或展示组件。
2. 扩展字段明显增多，字段名直接展示影响用户理解。
3. 同一个字段类型需要多种展示方式，例如标签、段落、Markdown、表格。

### 可选改造方向

1. 在提示词模板中增加独立的字段展示配置，例如 `outputFieldMetaJson`。
2. 将 `outputSchema` 升级为 `{ fields: [...] }` 结构，字段包含 `key`、`label`、`type`、`order`。
3. 后端继续用示例 JSON 约束 AI 输出，前端展示元数据单独维护，避免把 AI 输出约束和 UI 配置耦合过深。

## 公共 AI 配置缓存策略

- 当前状态：公共 AI 配置每次 AI 调用前查当前 active + enabled 配置。
- 当前结论：短期保持查表，保证管理员修改配置后立即生效。
- 后续方向：单机阶段可加本地内存缓存，管理员改配置时清缓存。
- 多实例方向：使用 Redis 缓存，或 Redis Pub/Sub 通知各实例清本地缓存。
- 注意：配置缓存和每日额度计数是两件事，额度统计后续可单独演进为每日额度表或 Redis 计数。

## 业务状态机精简评估

- 当前状态：项目里多张业务表使用 3-5 个状态值，整体还在可控范围内，但需要区分“真实生命周期状态”和“可以按字段或日期推导出的展示状态”。

> 可以保留的：
>
> `word_import_task.status = PENDING/RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED`
> 这个合理，`PARTIAL_SUCCESS` 有业务意义，不能简单用 `SUCCESS/FAILED` 替代。
>
> `async_task.status = PENDING/RUNNING/SUCCESS/FAILED`
> 标准异步任务状态，合理。
>
> `cloze_attempt_ai_review.status = RUNNING/DONE/FAILED`
> 也合理。不过如果它本质已经挂在 `async_task` 上，后面可以考虑只保留业务结果状态，避免双状态源。
>
> `user_word_state.mastery_status = NEW/LEARNING/REVIEWING/MASTERED/DIFFICULT`
> 这个不是普通任务状态，是学习算法状态，五个值可以接受。
>
> 需要再想想的：
>
> `study_plan.status = ACTIVE/PAUSED/COMPLETED/ENDED`
> 这里最容易语义重叠。`COMPLETED` 是“学完自动完成”，`ENDED` 是“用户手动结束/废弃”，如果你确实要区分这两种结局，就保留；如果前端和统计不区分，建议简化成 `ACTIVE/PAUSED/ENDED`，完成可由 `learned_count >= total_words` 推导。
>
> `daily_task.status = PENDING/DONE/EXPIRED`
> `EXPIRED` 如果只是根据 `task_date < today && not done` 推导出来，可以不存，查询时算。只有当你需要“某天被系统正式结算为过期”这种历史状态，才值得存。
>
> `daily_task_item.status = PENDING/DONE/SKIPPED`
> `SKIPPED` 如果现在没有跳过功能，就属于预留状态。预留不是大问题，但会让状态机看起来比实际复杂。可以先保留 `PENDING/DONE`，等真的做跳过再加。
>
> 我的建议：其他可以先保留,值得整理的是 `study_plan.status`、`daily_task.status`、`daily_task_item.status` 这三个。不要为了“状态少”强行删，优先删那些没有明确写入路径、前端没有展示差异、统计也不区分的状态。

- 暂不调整：`word_import_task.status`、`async_task.status`、`cloze_attempt_ai_review.status`、`user_word_state.mastery_status` 当前都有明确业务含义，先保持现状。
- 重点观察：`study_plan.status` 的 `COMPLETED` 和 `ENDED` 是否真的需要长期区分；如果前端、统计和运营不区分“自然完成”和“手动结束”，后续可考虑合并。
- 重点观察：`daily_task.status` 的 `EXPIRED` 是否需要落库；如果只是由 `task_date < today && status != DONE` 推导，可以改成查询或前端展示状态。
- 重点观察：`daily_task_item.status` 的 `SKIPPED` 是否有实际跳过功能；如果长期没有写入路径，可以先移除或等跳过功能上线时再补。
- 后续原则：不为了减少枚举数量而强行删状态；只清理没有写入路径、没有展示差异、统计也不区分的状态值。
  
## Other
`frontend/lexiflow-frontend/src/views/app/StudyCardView.vue:1335-1374` 这里一开始就塞进了一个空的 aiResult，但 catch 里没有把它清掉。结果是额度不足时，前端虽然会弹出“今日公共 AI 调用次数已用完”，对话框里却会从骨架页切成一个空白结果面板，而不是保持“还没有提问”或错误态。这个是实际可见的 UI 问题。
参考：StudyCardView.vue (line 1335)

`backend/lexiflow-backend/src/main/java/com/lexiflow/ai/content/service/WordAiContentService.java:92-115` 先发了 RUNNING 状态，再去做 checkQuota()。所以额度不足时，前端还是会短暂看到“生成中”，随后才报额度已用完。功能上没错，但和“直接明确提示额度不足”的目标相比，这个顺序会有一点闪烁感。
参考：WordAiContentService.java (line 92)

经过讨论，决定暂时不管这两个不重要的UI展示问题
