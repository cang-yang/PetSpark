package com.petspark.system;

import com.petspark.common.api.PageResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class SystemRepository {

    private final JdbcTemplate jdbcTemplate;

    public SystemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DictTypeView> findDictTypes() {
        return jdbcTemplate.query("""
                SELECT id, code, name, status, built_in, version
                FROM sys_dict_type ORDER BY code
                """, (rs, rowNum) -> new DictTypeView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("status"), rs.getBoolean("built_in"), rs.getInt("version")));
    }

    public Optional<DictTypeView> findDictType(String code) {
        return jdbcTemplate.query("""
                SELECT id, code, name, status, built_in, version
                FROM sys_dict_type WHERE code = ?
                """, rs -> rs.next() ? Optional.of(new DictTypeView(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("status"), rs.getBoolean("built_in"), rs.getInt("version"))) : Optional.empty(), code);
    }

    public void createDictType(String id, String code, String name) {
        jdbcTemplate.update("""
                INSERT INTO sys_dict_type (id, code, name, status, built_in)
                VALUES (?, ?, ?, 'ACTIVE', 0)
                """, id, code, name);
    }

    public List<DictItemView> findDictItems(String typeCode) {
        return jdbcTemplate.query("""
                SELECT id, type_code, item_key, item_label, sort_order, status, version
                FROM sys_dict_item WHERE type_code = ? ORDER BY sort_order, item_key
                """, (rs, rowNum) -> mapDictItem(rs), typeCode);
    }

    public Optional<DictItemView> findDictItem(String id) {
        return jdbcTemplate.query("""
                SELECT id, type_code, item_key, item_label, sort_order, status, version
                FROM sys_dict_item WHERE id = ?
                """, rs -> rs.next() ? Optional.of(mapDictItem(rs)) : Optional.empty(), id);
    }

    public void createDictItem(String id, String typeCode, String key, String label, int sortOrder, String status) {
        jdbcTemplate.update("""
                INSERT INTO sys_dict_item (id, type_code, item_key, item_label, sort_order, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, typeCode, key, label, sortOrder, status);
    }

    public int updateDictItem(String id, String label, int sortOrder, String status, int version) {
        return jdbcTemplate.update("""
                UPDATE sys_dict_item
                SET item_label = ?, sort_order = ?, status = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, label, sortOrder, status, id, version);
    }

    public List<SystemConfigView> findConfigs() {
        return jdbcTemplate.query("""
                SELECT id, config_key, config_value, value_type, description, protected_key, version
                FROM system_config ORDER BY config_key
                """, (rs, rowNum) -> mapConfig(rs));
    }

    public Optional<SystemConfigView> findConfig(String key) {
        return jdbcTemplate.query("""
                SELECT id, config_key, config_value, value_type, description, protected_key, version
                FROM system_config WHERE config_key = ?
                """, rs -> rs.next() ? Optional.of(mapConfig(rs)) : Optional.empty(), key);
    }

    public int updateConfig(String key, String value, String valueType, String description, int version) {
        return jdbcTemplate.update("""
                UPDATE system_config
                SET config_value = ?, value_type = ?, description = ?, version = version + 1
                WHERE config_key = ? AND version = ?
                """, value, valueType, description, key, version);
    }

    public PageResult<AuditLogView> findAuditLogs(String actorId, String module, String result, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(actorId)) {
            where.append(" AND actor_id = ? ");
            args.add(actorId.trim());
        }
        if (StringUtils.hasText(module)) {
            where.append(" AND module = ? ");
            args.add(module.trim());
        }
        if (StringUtils.hasText(result)) {
            where.append(" AND result = ? ");
            args.add(result.trim().toUpperCase(java.util.Locale.ROOT));
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<AuditLogView> items = jdbcTemplate.query("""
                SELECT id, request_id, actor_id, actor_role, module, action, object_type, object_id,
                       result, reason_code, ip_hash, created_at
                FROM audit_log
                %s
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> new AuditLogView(
                rs.getString("id"), rs.getString("request_id"), rs.getString("actor_id"),
                rs.getString("actor_role"), rs.getString("module"), rs.getString("action"),
                rs.getString("object_type"), rs.getString("object_id"), rs.getString("result"),
                rs.getString("reason_code"), rs.getString("ip_hash"), toInstant(rs.getTimestamp("created_at"))),
                pageArgs.toArray());
        return new PageResult<>(items, page, size, total == null ? 0 : total);
    }

    private DictItemView mapDictItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DictItemView(rs.getString("id"), rs.getString("type_code"), rs.getString("item_key"),
                rs.getString("item_label"), rs.getInt("sort_order"), rs.getString("status"), rs.getInt("version"));
    }

    private SystemConfigView mapConfig(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SystemConfigView(rs.getString("id"), rs.getString("config_key"), rs.getString("config_value"),
                rs.getString("value_type"), rs.getString("description"), rs.getBoolean("protected_key"),
                rs.getInt("version"));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
