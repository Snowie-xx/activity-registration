package com.example.activity.dto;

/**
 * 失败响应统一封装为 {"code": "...", "message": "..."}。
 */
public record ApiError(String code, String message) {
}
