package com.example.activity.web;

import com.example.activity.exception.BusinessException;

/**
 * X-User-Id 解析。缺失、非数字、非正数一律 400。
 * 注意：这只是本机演示标记，不是生产级认证（见 ARCHITECTURE.md 边界说明）。
 */
public final class UserId {

    private UserId() {
    }

    public static long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.invalidArgument("missing X-User-Id header");
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.invalidArgument("X-User-Id must be a positive integer");
        }
        if (value <= 0) {
            throw BusinessException.invalidArgument("X-User-Id must be a positive integer");
        }
        return value;
    }
}
