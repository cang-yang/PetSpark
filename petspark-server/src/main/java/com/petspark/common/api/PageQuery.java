package com.petspark.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询参数。{@code page} 从 1 开始，{@code size} 默认 20、最大 100。
 * 继承类可追加排序字段；排序字段名必须由服务端白名单校验，禁止直接拼接前端输入。
 *
 * <p>校验注解由 {@code spring-boot-starter-validation} 在 Controller 入参
 * {@code @Valid} 处触发，超限返回 400 {@code VALIDATION_PAGE_001/002}。
 */
public class PageQuery {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    @Min(value = 1, message = "page 必须从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "size 必须 >= 1")
    @Max(value = MAX_SIZE, message = "size 不得超过 " + MAX_SIZE)
    private int size = DEFAULT_SIZE;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /** 转为 MyBatis-Plus 等持久层使用的零基偏移量。 */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
