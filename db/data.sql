-- =====================================================================
-- 固定数据初始化 + 专用测试数据重置。
-- 报名记录初始为空；重复执行可安全重置（TRUNCATE 后重插，registration 自增归 1）。
-- 加载时请使用 utf8mb4 字符集，例如：
--   mysql --default-character-set=utf8mb4 -u root -p < data.sql
-- =====================================================================

USE activity_registration;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE registration;
TRUNCATE TABLE activity;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO activity (id, title, status, total_quota, remaining_quota) VALUES
  (1001, 'Java 实战分享',         'OPEN',   5,  5),
  (1002, '并发验收专用活动',       'OPEN',  10, 10),
  (1003, '零名额活动',             'OPEN',   0,  0),
  (1004, '已关闭活动',             'CLOSED', 5,  5),
  (1005, '请求键冲突验证活动',     'OPEN',   5,  5),
  (1006, '同用户不同键并发活动',   'OPEN',   5,  5),
  (1007, '同用户相同键并发活动',   'OPEN',   5,  5);
