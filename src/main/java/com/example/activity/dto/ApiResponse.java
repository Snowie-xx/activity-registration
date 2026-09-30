package com.example.activity.dto;

/**
 * 成功响应统一封装为 {"data": ...}。
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }
}
