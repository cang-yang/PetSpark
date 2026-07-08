package com.petspark.catalog;

import com.petspark.common.api.PageResult;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbcTemplate;

    public CatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<GoodsView> findPublicGoods(String categoryId, String keyword, int page, int size) {
        return findGoods(categoryId, keyword, "ACTIVE", false, page, size);
    }

    public PageResult<GoodsView> findAdminGoods(String categoryId, String keyword, String status, int page, int size) {
        return findGoods(categoryId, keyword, status, true, page, size);
    }

    private PageResult<GoodsView> findGoods(String categoryId, String keyword, String status, boolean includeInactive, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE g.deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (!includeInactive) {
            where.append(" AND g.status = 'ACTIVE' AND c.status = 'ACTIVE' ");
        } else if (StringUtils.hasText(status)) {
            where.append(" AND g.status = ? ");
            args.add(status.trim());
        }
        if (StringUtils.hasText(categoryId)) {
            where.append(" AND g.category_id = ? ");
            args.add(categoryId.trim());
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (g.sku LIKE ? OR g.name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM goods g JOIN goods_category c ON c.id = g.category_id" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<GoodsView> items = jdbcTemplate.query("""
                SELECT g.id, g.category_id, c.name AS category_name, g.sku, g.name, g.description,
                       g.cover_file_id, g.price, g.stock, g.status, g.version, g.created_at, g.updated_at
                FROM goods g
                JOIN goods_category c ON c.id = g.category_id
                %s
                ORDER BY g.created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> mapGoods(rs), pageArgs.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public Optional<GoodsView> findPublicGoodsById(String id) {
        return queryGoodsById(id, " AND g.status = 'ACTIVE' AND c.status = 'ACTIVE' ");
    }

    public Optional<GoodsView> findAdminGoodsById(String id) {
        return queryGoodsById(id, "");
    }

    private Optional<GoodsView> queryGoodsById(String id, String extraWhere) {
        return jdbcTemplate.query("""
                SELECT g.id, g.category_id, c.name AS category_name, g.sku, g.name, g.description,
                       g.cover_file_id, g.price, g.stock, g.status, g.version, g.created_at, g.updated_at
                FROM goods g
                JOIN goods_category c ON c.id = g.category_id
                WHERE g.id = ? AND g.deleted_at IS NULL %s
                """.formatted(extraWhere), rs -> rs.next() ? Optional.of(mapGoods(rs)) : Optional.empty(), id);
    }

    public void insertGoods(String id, GoodsUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO goods (id, category_id, sku, name, description, cover_file_id, price, stock, status, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, request.categoryId(), request.sku(), request.name(), request.description(),
                request.coverFileId(), request.price(), request.stock(), request.status());
    }

    public int updateGoods(String id, GoodsUpsertRequest request) {
        return jdbcTemplate.update("""
                UPDATE goods
                SET category_id = ?, sku = ?, name = ?, description = ?, cover_file_id = ?,
                    price = ?, stock = ?, status = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, request.categoryId(), request.sku(), request.name(), request.description(), request.coverFileId(),
                request.price(), request.stock(), request.status(), id, request.version());
    }

    public int updateStatus(String id, String status, int version) {
        return jdbcTemplate.update("""
                UPDATE goods SET status = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, status, id, version);
    }

    public int adjustStock(String id, int delta, int version) {
        return jdbcTemplate.update("""
                UPDATE goods
                SET stock = stock + ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND stock + ? >= 0
                """, delta, id, version, delta);
    }

    public void insertStockAdjustment(String adjustmentId, String goodsId, int delta, int before, int after, String reason, String operatorId) {
        jdbcTemplate.update("""
                INSERT INTO goods_stock_adjustment
                    (id, goods_id, delta_quantity, stock_before, stock_after, reason, operator_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, adjustmentId, goodsId, delta, before, after, reason, operatorId);
    }

    public List<GoodsCategoryView> findCategories(boolean includeInactive) {
        String where = includeInactive ? "WHERE deleted_at IS NULL" : "WHERE deleted_at IS NULL AND status = 'ACTIVE'";
        return jdbcTemplate.query("""
                SELECT id, code, name, status, sort_order, version
                FROM goods_category
                %s
                ORDER BY sort_order ASC, name ASC
                """.formatted(where), (rs, rowNum) -> new GoodsCategoryView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("status"),
                rs.getInt("sort_order"), rs.getInt("version")));
    }

    public void insertCategory(String id, GoodsCategoryRequest request) {
        jdbcTemplate.update("""
                INSERT INTO goods_category (id, code, name, status, sort_order, version)
                VALUES (?, ?, ?, ?, ?, 0)
                """, id, request.code(), request.name(), request.status(), request.sortOrder());
    }

    public int updateCategory(String id, GoodsCategoryRequest request) {
        return jdbcTemplate.update("""
                UPDATE goods_category
                SET code = ?, name = ?, status = ?, sort_order = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, request.code(), request.name(), request.status(), request.sortOrder(), id, request.version());
    }

    public boolean categoryExistsActive(String id) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods_category WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, Integer.class, id);
        return count != null && count > 0;
    }

    public boolean coverIsUsable(String fileId, String ownerId) {
        if (!StringUtils.hasText(fileId)) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM file_object
                WHERE id = ? AND owner_id = ? AND status = 'ACTIVE'
                  AND business_type = 'GOODS_COVER' AND deleted_at IS NULL
                """, Integer.class, fileId, ownerId);
        return count != null && count > 0;
    }

    public boolean isDuplicateKey(RuntimeException ex) {
        return ex instanceof DuplicateKeyException;
    }

    private GoodsView mapGoods(java.sql.ResultSet rs) throws java.sql.SQLException {
        String coverFileId = rs.getString("cover_file_id");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new GoodsView(
                rs.getString("id"),
                rs.getString("category_id"),
                rs.getString("category_name"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("description"),
                coverFileId,
                coverFileId == null ? null : "/api/v1/files/" + coverFileId,
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("status"),
                rs.getInt("version"),
                created == null ? null : created.toInstant(),
                updated == null ? null : updated.toInstant());
    }
}
