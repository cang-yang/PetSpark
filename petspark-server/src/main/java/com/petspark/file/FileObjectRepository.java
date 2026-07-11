package com.petspark.file;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FileObjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public FileObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(FileObject file) {
        jdbcTemplate.update("""
                INSERT INTO file_object
                    (id, object_key, original_name, media_type, extension, size_bytes, sha256,
                     width, height, status, owner_id, business_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, file.id(), file.objectKey(), file.originalName(), file.mediaType(), file.extension(),
                file.sizeBytes(), file.sha256(), file.width(), file.height(), file.status(),
                file.ownerId(), file.businessType());
    }

    public Optional<FileObject> findAvailable(String id) {
        return jdbcTemplate.query("""
                SELECT id, object_key, original_name, media_type, extension, size_bytes, sha256,
                       width, height, status, owner_id, business_type, confirmed_at
                FROM file_object
                WHERE id = ? AND deleted_at IS NULL AND status IN ('STAGED', 'ACTIVE')
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            Timestamp confirmed = rs.getTimestamp("confirmed_at");
            return Optional.of(new FileObject(
                    rs.getString("id"), rs.getString("object_key"), rs.getString("original_name"),
                    rs.getString("media_type"), rs.getString("extension"), rs.getLong("size_bytes"),
                    rs.getString("sha256"), (Integer) rs.getObject("width"), (Integer) rs.getObject("height"),
                    rs.getString("status"), rs.getString("owner_id"), rs.getString("business_type"),
                    confirmed == null ? null : confirmed.toInstant()));
        }, id);
    }

    public int confirm(String id, String ownerId) {
        return jdbcTemplate.update("""
                UPDATE file_object
                SET status = 'ACTIVE', confirmed_at = CURRENT_TIMESTAMP(3)
                WHERE id = ? AND owner_id = ? AND status = 'STAGED' AND deleted_at IS NULL
                """, id, ownerId);
    }

    public boolean existsActiveOwned(String id, String ownerId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM file_object
                WHERE id = ? AND owner_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, Integer.class, id, ownerId);
        return count != null && count > 0;
    }

    public boolean existsAvailable(String id) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM file_object
                WHERE id = ? AND deleted_at IS NULL AND status IN ('STAGED', 'ACTIVE')
                """, Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * 只有已确认且被当前公开业务对象引用的图片才允许匿名读取。
     * 上传中的文件、私有宠物图片以及下架商品图片均不会因此暴露。
     */
    public boolean isPubliclyReferenced(String id) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM file_object f
                WHERE f.id = ?
                  AND f.status = 'ACTIVE'
                  AND f.deleted_at IS NULL
                  AND (
                    EXISTS (
                        SELECT 1
                        FROM goods g
                        JOIN goods_category c ON c.id = g.category_id
                        WHERE g.cover_file_id = f.id
                          AND g.status = 'ACTIVE'
                          AND g.deleted_at IS NULL
                          AND c.status = 'ACTIVE'
                          AND c.deleted_at IS NULL
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM pet_image pi
                        JOIN pet p ON p.id = pi.pet_id
                        WHERE pi.file_id = f.id
                          AND p.public_status = 'PUBLISHED'
                          AND p.deleted_at IS NULL
                    )
                  )
                """, Integer.class, id);
        return count != null && count > 0;
    }

    public Optional<String> findStatusForOwner(String id, String ownerId) {
        return jdbcTemplate.query("""
                SELECT status
                FROM file_object
                WHERE id = ? AND owner_id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getString("status")) : Optional.empty(), id, ownerId);
    }
}
