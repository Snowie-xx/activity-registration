# 限量活动报名系统test

一个可以启动、可以演示、可以验证的最小业务系统：用户浏览限量活动、报名并查询自己的报名记录。
正确处理高并发抢名额、重复点击、请求重放与失败边界，全程使用**真实 MySQL + 真实 Redis**。

- 后端：Java 17 + Spring Boot 3.2.5 + Spring Data JPA + MySQL Connector/J 8.3.0
- 缓存：Spring Data Redis (Lettuce)
- 前端：单页 HTML + 原生 JavaScript（由 Spring Boot 直接托管，无构建步骤）
- 数据库：MySQL 8（演示环境使用 **MariaDB 10.11 LTS**，MySQL 协议兼容，见下方说明）

> **关于数据库**：本机网络无法访问 Oracle 的 MySQL 下载 CDN（`dev.mysql.com` / `cdn.mysql.com`
> 均返回 “Technical Difficulties”/404，详见 `AI_USAGE.md`）。因此演示环境使用 MariaDB 10.11.19，
> 它与 MySQL 8 协议兼容，本项目的 DDL/SQL 在 MySQL 8 上**无需任何修改即可运行**。
> 若你有一台可用的 MySQL 8，只需把 `DB_URL` 指向它即可。

---

## 1. 运行条件

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 或 21 | 本机使用 17.0.8 |
| Maven | 3.9.x | |
| MySQL | 8.x（或 MariaDB 10.11） | 端口 3306，库名 `activity_registration` |
| Redis | 5.x/6.x/7.x | 端口 6379，无认证 |

## 2. 初始化与启动命令

```bash
# 1) 启动 MySQL/MariaDB（端口 3306，root 无密码或按环境变量配置）
# 2) 初始化库表与固定数据（schema.sql + data.sql）
mysql --default-character-set=utf8mb4 -u root < db/schema.sql
mysql --default-character-set=utf8mb4 -u root < db/data.sql

# 3) 启动 Redis（端口 6379）
redis-server

# 4) 编译并启动应用（凭据可用环境变量覆盖，见 application.yml）
DB_USERNAME=root DB_PASSWORD= mvn spring-boot:run
# 或先打包再运行：
mvn -DskipTests package
java -jar target/activity-registration-1.0.0.jar
```

> 一键脚本见 `scripts/`：`init-db.cmd`、`start-mariadb.cmd`、`start-redis.cmd`、`run-app.cmd`。

## 3. 页面入口

启动后浏览器打开 **http://localhost:8080/**（单页演示）：

- 展示活动与剩余名额；输入演示用户 ID；选择活动报名；显示成功 / 业务错误；查询「我的报名」。
- 网络失败重试保留原 `requestId`，新报名意图才生成新键；错误在页面可见，不只输出到控制台。

## 4. 测试命令

```bash
# 纯单元测试（校验边界，无需外部组件）
mvn test

# 端到端验收集成测试（需 MySQL + Redis 已启动）
mvn verify
```

`AcceptanceIT`（`src/test/java/.../AcceptanceIT.java`）覆盖任务书「公开的关键验收」：
50 用户抢 10 名额、同键并发 20、异键并发 20、重放、参数冲突、跨用户隔离、满额后重放、
失败请求不绑定键、分页校验等。具体结果见 `TEST_REPORT.md`。

## 5. 接口速览

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET | `/api/activities` | 活动分页列表（含关闭/零名额，按 ID 升序） |
| GET | `/api/activities/{id}` | 活动详情（剩余名额实时取自 MySQL） |
| POST | `/api/registrations` | 报名（`X-User-Id` 头 + `{activityId, requestId}`），首次 201 / 重放 200 |
| GET | `/api/registrations` | 当前用户报名分页（创建时间降序，同时间 ID 降序） |
| GET | `/api/registrations/{id}` | 当前用户查看自己的一条报名 |

成功 `{"data":...}`；失败 `{"code":"...","message":"..."}`。错误码：
`INVALID_ARGUMENT`(400)、`ACTIVITY_NOT_FOUND`(404)、`REGISTRATION_NOT_FOUND`(404)、
`ACTIVITY_CLOSED`(409)、`SOLD_OUT`(409)、`ALREADY_REGISTERED`(409)、`IDEMPOTENCY_CONFLICT`(409)。

## 6. 完成度与已知缺陷

**已完成**：基础闭环全部；真实 MySQL/MariaDB + 真实 Redis 缓存（写入/命中/过期，含回源）；
并发名额控制、幂等重放、业务去重、参数冲突、跨用户隔离；单页演示；单元 + 集成测试；
数据库材料与全部文档。

**已知缺陷 / 边界**：
- `X-User-Id` 只是本机演示标记，**不是生产级认证**，无登录/签名/防伪造。
- 未实现活动管理后台、支付、取消报名、真实短信、登录系统、微服务（均为任务书明确不做项）。
- 深化方向（MQ/MongoDB/ES/Python 对账）**未选择**，详见 `TASKS.md` 的取舍说明。
- 缓存命中后仍会以一条轻量 SQL 回源取 `remaining_quota`，这是「剩余名额必须实时」的刻意取舍。

## 7. 目录结构

```
activity-registration/
├── db/            schema.sql（DDL）、data.sql（固定数据 + 重置）
├── scripts/       初始化/启动脚本
├── src/main/java/com/example/activity/
│   ├── controller/  HTTP 边界（Activity/Registration）
│   ├── service/     业务规则 + 事务 + 缓存
│   ├── repository/  持久化（含原子扣减 SQL）
│   ├── entity/      数据模型
│   ├── dto/         对外契约对象
│   ├── exception/   错误码 + 全局异常映射
│   └── web/         请求头/参数校验
├── src/main/resources/static/index.html   单页演示
└── src/test/java/   ValidationTest（单元）、AcceptanceIT（集成）
```
