package com.petspark.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果信封。字段顺序与接口设计文档一致：
 * {@code items, page, size, total}。业务模块返回分页时包成
 * {@link PageResult}，再由 {@link ApiResponse#okWithPage(PageResult)} 装入信封。
 *
 * <p>{@code page} 从 1 开始，{@code size} 由调用方约束在 [1, 100]，
 * {@code total} 为数据库命中总数（非当前页行数）。
 */
@JsonPropertyOrder({"items", "page", "size", "total"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PageResult<T> {

    private final List<T> items;
    private final long page;
    private final long size;
    private final long total;

    public PageResult(List<T> items, long page, long size, long total) {
        this.items = items == null ? Collections.emptyList() : items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public long getPage() {
        return page;
    }

    public long getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
