package com.petspark.ai.recommend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 商品候选检索器（PR-AI-03，候选类型 GOODS）。
 *
 * <p>可见性规则：goods.status='ACTIVE' AND goods_category.status='ACTIVE' AND goods.deleted_at IS NULL。
 * species 作为上下文不直接过滤（商品没有物种归属字段），但若需要可后续扩展按 goods.name/description 模糊匹配。
 * 按 created_at DESC 取 ≤20 条。
 *
 * <p>白名单投影：只暴露 id、type='GOODS'、publicSummary（name + categoryName，截断 120 字）、
 * matchedFacts（价格档位、库存线索）。不暴露内部成本价、供应商信息。
 *
 * <p>实现说明：CatalogRepository 是 public 且 findPublicGoods 已有 status='ACTIVE' 过滤，
 * 但为保持 retriever 接口一致性（统一的 isStillValid + 白名单投影），此处直接用 JdbcTemplate 查询，
 * 复用与 CatalogRepository.findPublicGoods 相同的过滤条件，避免引入额外 DTO 转换。
 */
@Repository
public class GoodsCandidateRetriever implements CandidateRetriever {

    private static final int MAX_CANDIDATES = 20;

    private final JdbcTemplate jdbcTemplate;

    public GoodsCandidateRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "GOODS";
    }

    @Override
    public List<Candidate> retrieve(String species, int age, String userId) {
        String sql = """
                SELECT g.id, g.name, g.description, g.price, g.stock, c.name AS category_name
                FROM goods g
                JOIN goods_category c ON g.category_id = c.id
                WHERE g.status = 'ACTIVE' AND c.status = 'ACTIVE' AND g.deleted_at IS NULL
                ORDER BY g.created_at DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id");
            String name = rs.getString("name");
            String description = rs.getString("description");
            String categoryName = rs.getString("category_name");
            java.math.BigDecimal price = rs.getBigDecimal("price");
            int stock = rs.getInt("stock");

            List<String> facts = new ArrayList<>();
            if (categoryName != null) {
                facts.add("分类:" + categoryName);
            }
            if (price != null) {
                facts.add("价格:" + price.toPlainString() + "元");
            }
            if (stock > 0) {
                facts.add("有货");
            } else {
                facts.add("缺货");
            }

            String summary = truncate(name + "（" + (categoryName == null ? "" : categoryName) + "）"
                    + (description == null ? "" : " " + description), 120);
            return new Candidate(id, "GOODS", summary, facts);
        }, MAX_CANDIDATES);
    }

    @Override
    public boolean isStillValid(String id, String userId) {
        // 再校验：goods.status='ACTIVE' AND category.status='ACTIVE' AND deleted_at IS NULL。
        // userId 不参与商品可见性（所有登录用户看到相同的上架商品）。
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM goods g
                JOIN goods_category c ON g.category_id = c.id
                WHERE g.id = ? AND g.status = 'ACTIVE' AND c.status = 'ACTIVE'
                  AND g.deleted_at IS NULL
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
