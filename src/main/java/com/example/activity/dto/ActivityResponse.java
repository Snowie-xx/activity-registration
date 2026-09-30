package com.example.activity.dto;

/**
 * 活动对外表示。remainingQuota 始终取自 MySQL 当前值。
 */
public record ActivityResponse(Long id, String title, String status, int totalQuota, int remainingQuota) {
}
