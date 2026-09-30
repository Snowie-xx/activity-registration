# 测试报告

## 0. 测试策略

至少三类验证证据（任务书「基础验证底线」）：

| 类别 | 证据来源 | 对应测试 |
| --- | --- | --- |
| 正常报名与重放 | 集成测试 + 日志 | `register_thenReplay_returnsSameRecord` |
| 并发名额 / 业务去重 | 集成测试（真实多线程 HTTP） | `quota_50UsersCompeteFor10`、`sameKey_20Concurrent`、`differentKeys_20Concurrent` |
| 事务失败 / 组件故障 | 集成测试 + 手工 | `failedRequest_doesNotBindKey`（事务回滚）、Redis 中断回源（手工） |

除 HTTP 契约外，同时核对数据库行数、剩余名额、缓存与回滚（见第 3 节 SQL 证据）。

## 1. 单元测试（`mvn test`，无需外部组件）

`src/test/java/com/example/activity/ValidationTest.java`：请求键 / 身份标记 / 分页参数校验边界。

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

覆盖：requestId 合法/非法（长度、字符集、大小写）、X-User-Id 正整数的缺失/非数字/非正数、分页 page/size 边界。

## 2. 端到端集成测试（`mvn verify`，需 MySQL + Redis）

`src/test/java/com/example/activity/AcceptanceIT.java` 用例与期望：

| 用例 | 期望 |
| --- | --- |
| register_thenReplay | 首次 201、重放 200 且同 ID，名额只扣 1 |
| validation_* | 缺失头/坏键/非正 userId/非法分页 → 400 |
| activityList_* | 7 条按 ID 升序；1003 零名额 OPEN、1004 CLOSED、9999 → 404 |
| quota_50Users | 10×201、40×SOLD_OUT，剩余 0、10 条记录 |
| sameKey_20Concurrent | 1×201、19×200，全部同一 ID，落库 1 条 |
| differentKeys_20Concurrent | 1×201、19×ALREADY_REGISTERED，剩余只扣 1 |
| idempotencyConflict | 同键异活动 → 409 IDEMPOTENCY_CONFLICT；换键同活动 → 409 ALREADY_REGISTERED |
| replayAfterFull | 满额后新用户 SOLD_OUT，已成功用户重放仍 200 |
| failedRequest_doesNotBindKey | 关闭/零名额失败后，同键可用于开放活动 |
| crossUserIsolation | 他人记录 404，列表隔离 |

> 实际运行结果（2026-09-30，`mvn verify`，MariaDB 10.11 + Redis 7 已启动）：

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  -- com.example.activity.AcceptanceIT
Tests run: 5,  Failures: 0, Errors: 0, Skipped: 0  -- com.example.activity.ValidationTest
```

> 运行命令：`mvn verify`（单元测试 `mvn test` 可单独运行，不依赖外部组件）。
> 启动集成测试前请先 `db/data.sql` 重置或依赖 `@BeforeEach` 自动重置。

## 3. 手工核对（数据库 / 缓存 / 回滚 / 持久化）

- **名额一致性**：`SELECT id, total_quota, remaining_quota, (SELECT COUNT(*) FROM registration r WHERE r.activity_id=a.id) AS success_count FROM activity a;`
  核对 `total_quota = remaining_quota + success_count`。
- **重复检查**：`SELECT user_id, activity_id, COUNT(*) c FROM registration GROUP BY user_id, activity_id HAVING c > 1;`（应为空）；
  同键重复同理按 `user_id, request_id` 分组。
- **Redis 写入/命中/过期**：日志出现 `redis cache set key=activity:info:1001`；再次请求出现 `redis cache hit`；
  `redis-cli TTL activity:info:1001` 随时间递减至 0 后消失（或 `redis-cli GET` 为 nil）。
- **Redis 中断回源**：停掉 Redis 后 `GET /api/activities/{id}` 仍 200（日志 `redis read failed, fallback to db`），
  报名接口照常工作。
- **事务回滚**：对已满活动报名，确认无新增 `registration` 行、`remaining_quota` 不变（插入与扣减一起回滚）。
- **重启持久化**：重启应用（或数据库）后，`GET /api/activities` 剩余名额与 `registration` 行数保持。

## 4. 真实 / 模拟 / 设计 区分

- **真实**：MySQL/MariaDB 持久化、Redis 缓存、多线程并发 HTTP、事务回滚 —— 均为实际运行验证。
- **模拟**：无（未用 H2/内存库冒充，未用进程内异步冒充 MQ）。
- **仅设计（未实现）**：深化方向 A/B/C/D 见 `TASKS.md`。
