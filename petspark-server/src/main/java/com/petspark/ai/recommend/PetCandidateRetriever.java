package com.petspark.ai.recommend;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 宠物候选检索器（PR-AI-03，候选类型 PET）。
 *
 * <p>可见性规则：用户可见的宠物 = 自己拥有的（无论 public_status）+ 他人公开的（public_status='PUBLISHED'）。
 * 仅查 deleted_at IS NULL。按 created_at DESC 取 ≤20 条。
 *
 * <p>白名单投影：只暴露 id、type='PET'、publicSummary（name + species + sex，截断 120 字）、
 * matchedFacts（物种匹配等线索）。不暴露 owner_user_id、birth_date、address 等敏感字段。
 *
 * <p>实现说明：PetRepository 为 package-private（位于 pet 包），无法从 ai.recommend 包注入，
 * 故此处直接用 JdbcTemplate 查询 pet 表——这是 V015 已落库的稳定 schema，不引入跨包依赖。
 */
@Repository
public class PetCandidateRetriever implements CandidateRetriever {

    private static final int MAX_CANDIDATES = 20;

    private final JdbcTemplate jdbcTemplate;

    public PetCandidateRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String type() {
        return "PET";
    }

    @Override
    public List<Candidate> retrieve(String species, int age, String userId) {
        // 可见性：自己拥有的（owner_user_id=userId）或他人公开的（public_status='PUBLISHED'）。
        // species 非空时按物种过滤（精确匹配，species 列已为 VARCHAR(32)）。
        boolean hasSpecies = species != null && !species.isBlank();
        String sql = """
                SELECT p.id, p.name, p.species, p.sex, p.public_status, p.adoption_status,
                       (SELECT pi.file_id FROM pet_image pi WHERE pi.pet_id = p.id
                        ORDER BY pi.cover_flag DESC, pi.sort_order ASC LIMIT 1) AS image_file_id
                FROM pet p
                WHERE p.deleted_at IS NULL
                  AND (p.owner_user_id = ? OR p.public_status = 'PUBLISHED')
                """;
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (hasSpecies) {
            sql += " AND p.species = ?";
            params.add(species.trim());
        }
        sql += " ORDER BY p.created_at DESC LIMIT ?";
        params.add(MAX_CANDIDATES);

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("id");
            String name = rs.getString("name");
            String sp = rs.getString("species");
            String sex = rs.getString("sex");
            String publicStatus = rs.getString("public_status");
            String adoptionStatus = rs.getString("adoption_status");

            List<String> facts = new ArrayList<>();
            if (sp != null) {
                facts.add("物种:" + sp);
            }
            if (sex != null && !"UNKNOWN".equals(sex)) {
                facts.add("性别:" + sex);
            }
            if ("PUBLISHED".equals(publicStatus)) {
                facts.add("公开可领养/参观");
            }
            if (adoptionStatus != null && !"NOT_FOR_ADOPTION".equals(adoptionStatus)) {
                facts.add("领养状态:" + adoptionStatus);
            }

            String summary = truncate(name + "（" + sp + "）", 120);
            String imageFileId = rs.getString("image_file_id");
            return new Candidate(id, "PET", summary, facts, name,
                    imageFileId == null ? null : "/api/v1/files/" + imageFileId,
                    sp, null, "/pets/" + id);
        }, params.toArray());
    }

    @Override
    public boolean isStillValid(String id, String userId) {
        // 再校验：deleted_at IS NULL 且（自己拥有 OR 公开）。
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pet
                WHERE id = ? AND deleted_at IS NULL
                  AND (owner_user_id = ? OR public_status = 'PUBLISHED')
                """, Integer.class, id, userId);
        return count != null && count > 0;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
