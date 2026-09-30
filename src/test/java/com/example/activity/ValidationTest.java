package com.example.activity;

import com.example.activity.exception.BusinessException;
import com.example.activity.web.PageParams;
import com.example.activity.web.RequestId;
import com.example.activity.web.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 纯单元测试：请求键 / 身份标记 / 分页参数的校验边界。
 * 无需 Spring 上下文与外部组件，`mvn test` 即可运行。
 */
class ValidationTest {

    @Test
    void requestId_validCases() {
        assertDoesNotThrow(() -> RequestId.validate("signup_001"));
        assertDoesNotThrow(() -> RequestId.validate("a"));
        assertDoesNotThrow(() -> RequestId.validate("A-b_C1"));
        assertDoesNotThrow(() -> RequestId.validate("k".repeat(64)));
        assertDoesNotThrow(() -> RequestId.validate("ABC-123_xyz"));
    }

    @Test
    void requestId_invalidCases() {
        assertInvalidRequestId(null);
        assertInvalidRequestId("");
        assertInvalidRequestId("k".repeat(65));
        assertInvalidRequestId("has space");
        assertInvalidRequestId("中文键");
        assertInvalidRequestId("A.b"); // 点号不在 [A-Za-z0-9_-] 内
        assertInvalidRequestId("key!");
    }

    @Test
    void requestId_isCaseSensitive() {
        // 大小写不同视为不同键（校验层允许，区分大小写由 DB utf8mb4_bin 保证）
        assertDoesNotThrow(() -> RequestId.validate("Key"));
        assertDoesNotThrow(() -> RequestId.validate("KEY"));
    }

    @Test
    void userId_validAndInvalid() {
        assertEquals(1101L, UserId.parse("1101"));
        assertEquals(1L, UserId.parse(" 1 "));
        assertInvalidUserId(null);
        assertInvalidUserId("");
        assertInvalidUserId("   ");
        assertInvalidUserId("abc");
        assertInvalidUserId("0");
        assertInvalidUserId("-1");
        assertInvalidUserId("1.5");
    }

    @Test
    void pageParams_validAndInvalid() {
        assertDoesNotThrow(() -> PageParams.validate(1, 10));
        assertDoesNotThrow(() -> PageParams.validate(1, 1));
        assertDoesNotThrow(() -> PageParams.validate(1, 100));
        assertInvalidPage(0, 10);
        assertInvalidPage(-1, 10);
        assertInvalidPage(1, 0);
        assertInvalidPage(1, 101);
    }

    private void assertInvalidRequestId(String value) {
        BusinessException e = assertThrows(BusinessException.class, () -> RequestId.validate(value));
        assertEquals("INVALID_ARGUMENT", e.getErrorCode().name());
    }

    private void assertInvalidUserId(String value) {
        BusinessException e = assertThrows(BusinessException.class, () -> UserId.parse(value));
        assertEquals("INVALID_ARGUMENT", e.getErrorCode().name());
    }

    private void assertInvalidPage(int page, int size) {
        BusinessException e = assertThrows(BusinessException.class, () -> PageParams.validate(page, size));
        assertEquals("INVALID_ARGUMENT", e.getErrorCode().name());
    }
}
