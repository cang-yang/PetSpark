package com.petspark.audit;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * audit_log 表的 MyBatis 映射。审计只追加、不更新，故只需 insert 与查询。
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 追加一条审计日志。字段与 {@link AuditLog} 一一对应。
     */
    void insert(AuditLog log);

    /**
     * 按 id 查询，主要用于测试与审计复核。
     */
    @Select("SELECT id, request_id, actor_id, actor_role, module, action, object_type, "
            + "object_id, result, reason_code, ip_hash, created_at "
            + "FROM audit_log WHERE id = #{id}")
    Optional<AuditLog> findById(@Param("id") String id);

    /**
     * 按操作者统计，用于测试断言落库成功。
     */
    @Select("SELECT COUNT(*) FROM audit_log WHERE actor_id = #{actorId}")
    long countByActor(@Param("actorId") String actorId);
}
