# 待办事项

## 完形填空生成状态刷新恢复

- 现状：用户在 `/app/cloze` 生成完形填空时，如果请求耗时较长就手动刷新页面，前端本地的 `generating` 状态会丢失。
- 现状：后端虽然已经创建了 `async_task` 记录，但当前前端没有在刷新后自动根据 `taskId` 恢复“生成中”页面。
- 影响：用户会看到页面恢复为空闲态，误以为生成中断；实际后端可能还在继续生成，或者已经生成完成但前端没有接回结果。

### 建议改造

1. 创建完形填空任务时，前端先拿到并持久化 `taskId`。
2. 前端刷新后优先读取本地保存的 `taskId`。
3. 前端通过 `GET /api/v1/tasks/{taskId}` 轮询任务状态。
4. 当状态为 `RUNNING` 时，恢复“正在生成完形填空”的提示。
5. 当状态为 `SUCCESS` 时，自动拿到 `resultId` 并跳转到题目详情。
6. 当状态为 `FAILED` 时，恢复错误提示并清理本地缓存。

### 备注

- 这条待办和 AI 评阅的刷新恢复是两件事，前者是“完形填空生成任务”，后者是“作答后 AI 评阅任务”。
- `docs/seed-review-words.ps1` 仍然可用于制造复习词，方便配合此类异步任务测试。

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
  
## Other
`frontend/lexiflow-frontend/src/views/app/StudyCardView.vue:1335-1374` 这里一开始就塞进了一个空的 aiResult，但 catch 里没有把它清掉。结果是额度不足时，前端虽然会弹出“今日公共 AI 调用次数已用完”，对话框里却会从骨架页切成一个空白结果面板，而不是保持“还没有提问”或错误态。这个是实际可见的 UI 问题。
参考：StudyCardView.vue (line 1335)

`backend/lexiflow-backend/src/main/java/com/lexiflow/ai/content/service/WordAiContentService.java:92-115` 先发了 RUNNING 状态，再去做 checkQuota()。所以额度不足时，前端还是会短暂看到“生成中”，随后才报额度已用完。功能上没错，但和“直接明确提示额度不足”的目标相比，这个顺序会有一点闪烁感。
参考：WordAiContentService.java (line 92)

经过讨论，决定暂时不管这两个不重要的UI展示问题