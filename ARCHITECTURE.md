# 架构设计说明

## 1. 模块关系与职责划分

按「HTTP 边界 → 业务规则 → 持久化 → 外部组件」四层组织，每层职责单一、依赖方向单向：

```
controller (HTTP 边界)
   │  校验 X-User-Id / 参数 / 分页，转调 service，组装统一响应
   ▼
service (业务规则 + 事务 + 缓存)
   │  RegistrationService  编排：委托事务核心 + 解析并发唯一键冲突 + 查询隔离
   │  RegistrationTx       事务核心：重放/去重/名额判定的执行顺序与原子提交
   │  ActivityService      活动列表/详情 + 缓存回源
   │  ActivityCacheService Redis 缓存读写 + 故障回源
   ▼
repository (持久化)
   │  JPA + 显式 SQL：原子条件 UPDATE 扣名额、唯一约束查找、分页
   ▼
MySQL/MariaDB（数据与约束） + Redis（基础信息缓存）
```

**为何这样划分**：事务边界是正确性关键。`RegistrationTx` 是唯一带 `@Transactional` 的写路径，
「插入报名 + 扣减名额」要么一起提交要么一起回滚；`RegistrationService` 自身不参与事务，
这样才能在事务核心回滚后，于独立事务中把唯一键冲突解析为业务响应（而不是把数据库异常抛给客户端）。

**增加功能时改哪里**：
- 新查询接口 → 新增 controller 方法 + service 方法（读路径不动事务核心）。
- 新报名规则 → 改 `RegistrationTx` 的判定顺序与 `RegistrationService.resolveConflict`。
- 新活动字段 → 改 entity + DTO + `db/schema.sql`，并在 `ActivityCacheService` 明确哪些可缓存、哪些必须回源。

## 2. 数据模型

- `activity(id PK, title, status, total_quota, remaining_quota, created_at, updated_at)`
  - `CHECK (remaining_quota >= 0)`：数据库层兜底防负名额。
- `registration(id PK 自增, activity_id, user_id, request_id, status, created_at)`
  - `UNIQUE uk_user_activity (user_id, activity_id)`：**业务去重**——同用户对同活动只能成功一次。
  - `UNIQUE uk_user_request (user_id, request_id)`：**幂等键**——键在用户作用域内唯一绑定，`utf8mb4_bin` 区分大小写。
  - `KEY idx_user_created (user_id, created_at, id)`：支撑「我的报名」按创建时间降序查询。
  - `FK activity_id → activity(id)`：报名必须指向存在的活动。

**关键点**：`requestId` 不单独建表，而是报名记录上的一个唯一约束列。只有成功提交的报名才落库，
因此「失败请求不占用键」天然成立——失败的插入会被事务回滚，键从未持久化。

## 3. 事务 / 并发 / 幂等

写路径 `RegistrationTx.register` 在同一事务内按**任务书规定的判定顺序**执行：

1. **幂等键重放 / 参数冲突**：`findByUserIdAndRequestId`。命中且同活动 → 返回原记录（200）；
   命中但不同活动 → `IDEMPOTENCY_CONFLICT`(409)。
2. **业务去重**：`findByUserIdAndActivityId`。命中 → `ALREADY_REGISTERED`(409)。
3. **活动满额 / 关闭**：读活动。关闭 → `ACTIVITY_CLOSED`；剩余 ≤ 0 → `SOLD_OUT`。
4. **原子扣减名额**：`UPDATE ... SET remaining_quota = remaining_quota - 1
   WHERE id=? AND remaining_quota > 0 AND status='OPEN'`，受影响行数为 0 则回滚并报满额/关闭。
   扣减先于插入执行，先取活动行 X 锁——避免「先插入」时外键校验对 activity 的 S 锁与扣减 X 锁
   在并发下形成 S→X 升级死锁。
5. **插入报名**：`saveAndFlush` 立即 flush，让唯一键冲突在此暴露；冲突时事务回滚会一并撤销上面的扣减。

**并发正确性由数据库保证**，应用层不做分布式锁、不信任缓存余量：

- **不超卖**：条件 UPDATE 是 InnoDB 的「当前读」，行锁串行化，`remaining_quota > 0` 保证名额永不为负。
  50 用户抢 10 名额 → 恰好 10 次成功、40 次 `SOLD_OUT`。
- **同键并发收敛**：`uk_user_request` 使并发插入同键的 19 个事务在唯一索引上等待，胜者提交后其余收到
  重复键错误；外层捕获 `DataIntegrityViolationException` 并解析为**重放**（同活动）或**参数冲突**（异活动）。
- **异键并发只成功一次**：`uk_user_activity` 使同用户对同活动的并发异键插入只有一个成功，其余解析为
  `ALREADY_REGISTERED`。
- **跨活动同键并发**：`uk_user_request` 保证同键只能绑定一个活动，另一个返回 `IDEMPOTENCY_CONFLICT`。
  失败方即使先扣减了名额，也会因唯一键冲突回滚而撤销扣减，不会多扣任何活动名额。
- **原子性**：扣减后若插入失败（唯一键冲突）则抛异常 → 整体回滚，报名与扣减一起撤销，
  失败请求不落库、不占用键、不多扣名额。
- **不泄露内部异常**：唯一键冲突被捕获解析为 409 业务响应；真正未预期的异常才返回 500，且不暴露堆栈。

## 4. 缓存设计

- **缓存内容**：活动基础信息 `{id, title, status, totalQuota}`（键 `activity:info:{id}`），TTL 可配置
  （`app.cache.ttl-seconds`，默认 120s）。**不缓存 `remainingQuota`**。
- **剩余名额实时**：详情接口在缓存命中时仍以一条轻量 SQL `SELECT remaining_quota` 回源；
  列表接口直接读库（剩余名额必须实时，缓存只服务详情的基础信息）。
- **写路径不读缓存**：报名资格与名额判定全部走 MySQL 条件 UPDATE，缓存对写路径零影响。
- **故障回源**：`ActivityCacheService` 对 Redis 读写全部 try/catch，Redis 不可用或超时 → 返回 miss /
  跳过写入 → 活动查询回源 MySQL，报名完全不受影响。连接/读取超时有限（`timeout`/`connect-timeout` 各 1s）。
- **证据**：命中/写入/回源均打日志（`redis cache hit/set key=...`），可用 `redis-cli` 与日志对照验证。

## 5. 选型与生产差距

| 项 | 选型 | 生产差距 |
| --- | --- | --- |
| 持久层 | Spring Data JPA + 显式 SQL | 生产可换 MyBatis/加读写分离；当前单库 |
| 并发控制 | 唯一约束 + 条件 UPDATE | 已满足单库场景；跨库/分布式需另选方案 |
| 缓存 | Redis 基础信息缓存 | 无热点重建/预热；无分布式锁 |
| 身份 | `X-User-Id` 演示标记 | **非生产认证**，无登录/签名/防伪造 |
| 深层次 | 未选择（MQ/MongoDB/ES/对账） | 见 TASKS.md 取舍 |

> 架构文字均可在代码中定位：并发判定顺序在 `RegistrationTx.java`，冲突解析在
> `RegistrationService.resolveConflict`，原子扣减在 `ActivityRepository.decrementQuota`，
> 缓存回源在 `ActivityCacheService.java`。
