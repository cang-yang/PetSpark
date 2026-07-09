package com.petspark.banner;

import com.petspark.common.api.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class BannerRepository {

    private final JdbcTemplate jdbcTemplate;

    public BannerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BannerView> findPublicActive(int limit) {
        Timestamp now = timestamp(Instant.now());
        return jdbcTemplate.query("""
                SELECT id, title, subtitle, image_url, target_type, target_url, status, sort_order,
                       starts_at, ends_at, version, created_at, updated_at
                FROM operation_banner
                WHERE deleted_at IS NULL AND status = 'ACTIVE'
                  AND (starts_at IS NULL OR starts_at <= ?)
                  AND (ends_at IS NULL OR ends_at > ?)
                ORDER BY sort_order ASC, created_at DESC
                LIMIT ?
                """, (rs, rowNum) -> map(rs), now, now, limit);
    }

    public PageResult<BannerView> findAdmin(String keyword, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ? ");
            args.add(status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (title LIKE ? OR subtitle LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_banner" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<BannerView> items = jdbcTemplate.query("""
                SELECT id, title, subtitle, image_url, target_type, target_url, status, sort_order,
                       starts_at, ends_at, version, created_at, updated_at
                FROM operation_banner
                %s
                ORDER BY sort_order ASC, created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> map(rs), pageArgs.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    public Optional<BannerView> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, title, subtitle, image_url, target_type, target_url, status, sort_order,
                       starts_at, ends_at, version, created_at, updated_at
                FROM operation_banner
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), id);
    }

    public void insert(String id, BannerUpsertRequest request) {
        jdbcTemplate.update("""
                INSERT INTO operation_banner
                    (id, title, subtitle, image_url, target_type, target_url, status, sort_order, starts_at, ends_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, request.title(), request.subtitle(), request.imageUrl(), request.targetType(), request.targetUrl(),
                request.status(), request.sortOrder(), timestamp(request.startsAt()), timestamp(request.endsAt()));
    }

    public int update(String id, BannerUpsertRequest request) {
        return jdbcTemplate.update("""
                UPDATE operation_banner
                SET title = ?, subtitle = ?, image_url = ?, target_type = ?, target_url = ?, status = ?,
                    sort_order = ?, starts_at = ?, ends_at = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, request.title(), request.subtitle(), request.imageUrl(), request.targetType(), request.targetUrl(),
                request.status(), request.sortOrder(), timestamp(request.startsAt()), timestamp(request.endsAt()),
                id, request.version());
    }

    public int updateStatus(String id, String status, int version) {
        return jdbcTemplate.update("""
                UPDATE operation_banner
                SET status = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, status, id, version);
    }

    public int updateSortOrder(String id, int sortOrder, int version) {
        return jdbcTemplate.update("""
                UPDATE operation_banner
                SET sort_order = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, sortOrder, id, version);
    }

    public int softDelete(String id, int version) {
        return jdbcTemplate.update("""
                UPDATE operation_banner
                SET deleted_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, id, version);
    }

    private BannerView map(ResultSet rs) throws SQLException {
        Timestamp startsAt = rs.getTimestamp("starts_at");
        Timestamp endsAt = rs.getTimestamp("ends_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new BannerView(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("image_url"),
                rs.getString("target_type"),
                rs.getString("target_url"),
                rs.getString("status"),
                rs.getInt("sort_order"),
                startsAt == null ? null : startsAt.toInstant(),
                endsAt == null ? null : endsAt.toInstant(),
                rs.getInt("version"),
                createdAt == null ? null : createdAt.toInstant(),
                updatedAt == null ? null : updatedAt.toInstant());
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
