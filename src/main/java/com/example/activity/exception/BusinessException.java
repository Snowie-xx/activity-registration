package com.example.activity.exception;

/**
 * 可预期的业务异常，统一由 GlobalExceptionHandler 映射为约定错误响应，
 * 不会向客户端泄露数据库唯一键等内部异常细节。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BusinessException invalidArgument(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    public static BusinessException activityNotFound() {
        return new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND, "activity not found");
    }

    public static BusinessException registrationNotFound() {
        return new BusinessException(ErrorCode.REGISTRATION_NOT_FOUND, "registration not found");
    }

    public static BusinessException activityClosed() {
        return new BusinessException(ErrorCode.ACTIVITY_CLOSED, "activity is closed");
    }

    public static BusinessException soldOut() {
        return new BusinessException(ErrorCode.SOLD_OUT, "activity is sold out");
    }

    public static BusinessException alreadyRegistered() {
        return new BusinessException(ErrorCode.ALREADY_REGISTERED, "already registered for this activity");
    }

    public static BusinessException idempotencyConflict() {
        return new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "requestId already bound to another activity");
    }
}
