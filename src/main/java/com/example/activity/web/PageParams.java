package com.example.activity.web;

import com.example.activity.exception.BusinessException;

/**
 * 分页参数校验：page &ge; 1，1 &le; size &le; 100。
 */
public final class PageParams {

    private PageParams() {
    }

    public static void validate(int page, int size) {
        if (page < 1) {
            throw BusinessException.invalidArgument("page must be >= 1");
        }
        if (size < 1 || size > 100) {
            throw BusinessException.invalidArgument("size must be between 1 and 100");
        }
    }
}
