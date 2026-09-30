package com.example.activity.dto;

/**
 * 报名对外表示。id 以字符串返回（验收按字符串比较）。
 */
public record RegistrationResponse(String id, Long activityId, Long userId, String requestId,
                                   String status, String createdAt) {
}
