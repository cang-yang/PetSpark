package com.petspark.ai.recommend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 服务项目候选检索器（PR-AI-03，候选类型 SERVICE）。
 *
 * <p>可见性规则：service_item.status='ACTIVE' AND service_item.deleted_at IS NULL。
 * species 作为上下文不直接过滤（服务项目没有物种归属字段）。
 * 按 created_at DESC 取 ≤20 条。
 *
 * <p>白名单投影：只暴露 id、type='SERVICE'、publicSummary（name + kind，截断 120 字）、
 * matchedFacts（kind 类别、资质线索）。不暴露内部成本、资源排期细节。
 *
 * <p>实现说明：ServiceRecords（含 ServiceItemRow）为 package-private（final class，位于 service 包），
 * 无法从 ai.recommend 包注入访问。ServiceBookingRepository.findItem() 虽 public 但不过滤 status='ACTIVE'，
 * 仅查 deleted_at IS NULL——不足以满足 NFR-AI-001 的"当前有效"要求。
 * 故此处直接用 JdbcTemplate 查询 service_item 表，复用 V022 已落库的稳定 schema，
 * 在 retrieve 和 isStillValid 中都显式校验 status='ACTIVE'。
 */
@Repository
public class ServiceCandidateRetriever implements CandidateRetriever {

    private static final int MAX_CANDIDATES = 20;

    private final JdbcTemplate jdbcTemplate;

    public ServiceCandidateRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "SERVICE";
    }

    @Override
    public List<Candidate> retrieve(String species, int age, String userId) {
        String sql = """
                SELECT id, kind, code, name, description, qualification, base_price
                FROM service_item
                WHERE status = 'ACTIVE' AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id");
            String kind = rs.getString("kind");
            String name = rs.getString("name");
            String description = rs.getString("description");
            String qualification = rs.getString("qualification");
            java.math.BigDecimal basePrice = rs.getBigDecimal("base_price");

            List<String> facts = new ArrayList<>();
            if (kind != null) {
                facts.add("类型:" + kind);
            }
            if (qualification != null && !qualification.isBlank()) {
                facts.add("资质:" + truncate(qualification, 40));
            }
            if (basePrice != null) {
                facts.add("起步价:" + basePrice.toPlainString() + "元");
            }

            String summary = truncate(name + "（" + (kind == null ? "" : kind) + "）"
                    + (description == null ? "" : " " + description), 120);
            return new Candidate(id, "SERVICE", summary, facts);
        }, MAX_CANDIDATES);
    }

    @Override
    public boolean isStillValid(String id, String userId) {
        // 再校验：status='ACTIVE' AND deleted_at IS NULL。
        // 关键：ServiceBookingRepository.findItem() 只查 deleted_at IS NULL 不过滤 status，
        // 故本 retriever 必须自行校验 status='ACTIVE'（NFR-AI-001 安全核心）。
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM service_item
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, Integer.class, id);
        return count != null && count > 0;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
