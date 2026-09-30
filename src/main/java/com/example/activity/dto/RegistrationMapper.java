package com.example.activity.dto;

import com.example.activity.entity.Registration;

import java.time.format.DateTimeFormatter;

/**
 * Registration 实体 → 对外 DTO。id 以字符串返回（验收按字符串比较）。
 */
public final class RegistrationMapper {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private RegistrationMapper() {
    }

    public static RegistrationResponse toResponse(Registration r) {
        return new RegistrationResponse(
                String.valueOf(r.getId()),
                r.getActivityId(),
                r.getUserId(),
                r.getRequestId(),
                r.getStatus().name(),
                r.getCreatedAt() == null ? null : FORMATTER.format(r.getCreatedAt()));
    }
}
