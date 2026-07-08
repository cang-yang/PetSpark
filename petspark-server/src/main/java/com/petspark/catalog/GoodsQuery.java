package com.petspark.catalog;

import com.petspark.common.api.PageQuery;

public class GoodsQuery extends PageQuery {
    private String categoryId;
    private String keyword;
    private String status;

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
