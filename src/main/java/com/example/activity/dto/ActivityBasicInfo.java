package com.example.activity.dto;

/**
 * 缓存在 Redis 中的活动基础信息（标题/状态/总名额）。
 * 注意：不包含 remainingQuota，剩余名额必须实时从 MySQL 读取。
 */
public record ActivityBasicInfo(Long id, String title, String status, int totalQuota) {
}
