# AI 使用记录

## 工具与范围

- 使用 **Claude Code（CLI）** 进行需求拆解、架构设计、编码、测试排障与文档撰写。
- 全程由我负责设计决策、代码审查与结果验证；AI 负责加速编码与信息检索。

## 关键任务清单

1. 解析 `机试任务书.docx`，提取业务规则、接口契约、固定数据与验收要点。
2. 勘察本机环境（JDK/Maven/MySQL/Redis/网络），确定可行技术栈。
3. 设计并实现报名事务核心（幂等/去重/名额/并发判定顺序）。
4. 设计 Redis 基础信息缓存与回源边界。
5. 编写单元测试与端到端集成测试（50 用户/同键/异键并发等）。
6. 编写 DDL/固定数据/页面/五份交付文档。

## 采纳 / 修改 / 否决（含验证）

### 例 1：并发名额控制方案（采纳「唯一约束 + 条件 UPDATE」，否决显式行锁为主）

- **候选**：a) `SELECT ... FOR UPDATE` 行锁；b) 唯一约束 + 原子条件 UPDATE。
- **结论**：采纳 b。条件 `UPDATE ... WHERE remaining_quota > 0 AND status='OPEN'` 是 InnoDB
  当前读，行锁串行化后 `>0` 条件杜绝超卖；业务去重与幂等由两条唯一约束兜底；插入放在扣减之前，
  使「扣减失败→回滚插入」与「重复键→外层解析」都无需在事务内做补偿。
- **验证**：`AcceptanceIT.quota_50UsersCompeteFor10` 断言 50 并发下 10 成功/40 满额、剩余 0；
  `sameKey_20Concurrent` 断言 1 新建/19 重放、同一 ID。

### 例 2：事务与唯一键冲突的边界分离（采纳「编排层 + 事务核心」两段式）

- **问题**：在 `@Transactional` 方法内捕获 `DuplicateKeyException` 会导致会话进入回滚态，
  后续查询不可靠。
- **结论**：`RegistrationService`（无事务）捕获 `DataIntegrityViolationException`，此时事务核心已回滚，
  再于独立事务中读取已提交记录，解析为「重放 / 参数冲突 / 业务去重」；绝不让数据库唯一键异常直接到达客户端。
- **验证**：集成测试覆盖同键并发重放、跨活动同键冲突、异键去重，均返回约定 200/409 而非 500。

### 例 3：数据库选型（否决 H2 嵌入式，采纳 MariaDB 作为 MySQL 兼容回退）

- **现象**：Oracle 的 MySQL 下载（`dev.mysql.com/get`、`cdn.mysql.com`、`downloads.mysql.com/archives`）
  均返回 “Technical Difficulties”/404，国内镜像无 MySQL；仅 GitHub 与高校镜像可达。
- **结论**：否决用 H2 冒充「真实 MySQL」（那属于模拟实现）；采纳 MariaDB 10.11 LTS（协议兼容，
  DDL/SQL 在 MySQL 8 上零修改可运行）。在 `README.md` 明确这一替换与理由，`DB_URL` 可一键切回 MySQL 8。
- **验证**：DDL 中 CHECK/唯一约束/`utf8mb4_bin` 均按 MySQL 8 与 MariaDB 10.11 双兼容语法编写。

## 未使用 / 依赖声明

- 未使用 MQ / MongoDB / ES / Python 对账（深化方向），原因见 `TASKS.md`。
- 所有「真实接入」均为实际运行的 MySQL/MariaDB + Redis；无任何用内存库或进程内异步冒充的替代实现。
