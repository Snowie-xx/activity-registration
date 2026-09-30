package com.example.activity.web;

import com.example.activity.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * requestId 校验：1—64 位 ASCII，匹配 [A-Za-z0-9_-]{1,64}，区分大小写。
 */
public final class RequestId {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private RequestId() {
    }

    public static void validate(String raw) {
        if (raw == null || !PATTERN.matcher(raw).matches()) {
            throw BusinessException.invalidArgument(
                    "requestId must be 1-64 ASCII chars matching [A-Za-z0-9_-]");
        }
    }
}
