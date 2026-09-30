-- =====================================================================
-- 限量活动报名系统 DDL（MySQL 8 / MariaDB 10.11 兼容）
-- 应用以 spring.jpa.hibernate.ddl-auto=none 运行，表结构完全由此文件管理，
-- 保证「数据库建表答案」与代码、评审可复现。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS activity_registration
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE activity_registration;

-- 活动。剩余名额只允许通过条件 UPDATE 原子扣减，配合 CHECK 约束双重防负。
CREATE TABLE IF NOT EXISTS activity (
    id               BIGINT       NOT NULL COMMENT '固定业务编号 1001..1007',
    title            VARCHAR(255) NOT NULL,
    status           VARCHAR(16)  NOT NULL COMMENT 'OPEN / CLOSED',
    total_quota      INT          NOT NULL,
    remaining_quota  INT          NOT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_activity_remaining CHECK (remaining_quota >= 0),
    CONSTRAINT chk_activity_total CHECK (total_quota >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 报名。两条唯一约束是并发/幂等正确性的根基：
--   uk_user_activity：同用户对同活动只能成功一次（业务去重）。
--   uk_user_request：  请求键在用户作用域内唯一绑定（幂等）。
-- request_id 用 utf8mb4_bin 以区分大小写。
CREATE TABLE IF NOT EXISTS registration (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    activity_id BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    request_id  VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '幂等键，区分大小写',
    status      VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    UNIQUE KEY uk_user_request (user_id, request_id),
    KEY idx_user_created (user_id, created_at, id),
    CONSTRAINT fk_reg_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
