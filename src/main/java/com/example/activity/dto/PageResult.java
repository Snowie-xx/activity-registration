package com.example.activity.dto;

import java.util.List;

/**
 * 分页列表结果：data.items / total / page / size。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {
}
