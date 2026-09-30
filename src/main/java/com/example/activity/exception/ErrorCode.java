package com.example.activity.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码与 HTTP 状态的一一映射，与接口契约一致。
 */
public enum ErrorCode {

    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST),
    ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND),
    REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    ACTIVITY_CLOSED(HttpStatus.CONFLICT),
    SOLD_OUT(HttpStatus.CONFLICT),
    ALREADY_REGISTERED(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
