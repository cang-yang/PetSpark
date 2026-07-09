package com.petspark.adoption;

import com.petspark.common.api.PageResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 领养申请持久化仓储。基于 JdbcTemplate，与 order/pet 同风格。
 *
 * <p>关键并发设计：
 * <ul>
 *   <li>同一宠物仅允许一条未结束申请。未结束＝status IN
 *       (PENDING, APPROVED, COMPLETED)。REJECTED/WITHDRAWN/CANCELLED
 *       不阻止再次申请。MySQL 无 partial unique index，故在 Service 层
 *       先 {@code SELECT ... FOR UPDATE} 锁 pet 行后复查申请，并配合
 *       pet.version 乐观锁兜底并发双申请。</li>
 *   <li>状态迁移 SQL 在 WHERE 带状态前置条件 + version 乐观锁，返回影响行数；
 *       0 时由 Service 区分版本冲突与状态非法。</li>
 * </ul>
 */
@Repository
public class AdoptionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdoptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 申请原始行。 */
    public record ApplicationRow(
            String id,
            String applicationNo,
            String petId,
            String applicantUserId,
            String statement,
            String profileSnapshot,
            String status,
            String reviewerUserId,
            String reviewNote,
            Instant decidedAt,
            Instant withdrawnAt,
            String withdrawReason,
            String handoverNote,
            Instant handoverAt,
            Instant createdAt,
            int version) {
    }

    /** 申请提交时尚未结束申请的存在性检查（同一宠物）。 */
    public boolean existsActiveForPet(String petId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM adoption_application
                WHERE pet_id = ? AND deleted_at IS NULL
                  AND status IN ('PENDING', 'APPROVED', 'COMPLETED')
                """, Integer.class, petId);
        return count != null && count > 0;
    }

    /** 申请提交时按申请人对同一宠物存在未结束申请检查（申请人对同一宠物重复申请）。 */
    public boolean existsActiveForPetByApplicant(String petId, String applicantUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM adoption_application
                WHERE pet_id = ? AND applicant_user_id = ? AND deleted_at IS NULL
                  AND status IN ('PENDING', 'APPROVED', 'COMPLETED')
                """, Integer.class, petId, applicantUserId);
        return count != null && count > 0;
    }

    /** 按幂等键加载本人申请（同 applicant + idempotency_key 命中即幂等重放）。 */
    public Optional<ApplicationRow> findByIdempotency(String applicantUserId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, application_no, pet_id, applicant_user_id, statement, profile_snapshot,
                       status, reviewer_user_id, review_note, decided_at, withdrawn_at,
                       withdraw_reason, handover_note, handover_at, created_at, version
                FROM adoption_application
                WHERE applicant_user_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(),
                applicantUserId, idempotencyKey);
    }

    /** 插入新申请。 */
    public void insert(String id, String applicationNo, String petId, String applicantUserId,
                        String statement, String profileSnapshot, String idempotencyKey) {
        jdbcTemplate.update("""
                INSERT INTO adoption_application
                    (id, application_no, pet_id, applicant_user_id, statement, profile_snapshot,
                     status, idempotency_key, version)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, 0)
                """, id, applicationNo, petId, applicantUserId, statement, profileSnapshot,
                StringUtils.hasText(idempotencyKey) ? idempotencyKey : null);
    }

    public Optional<ApplicationRow> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, application_no, pet_id, applicant_user_id, statement, profile_snapshot,
                       status, reviewer_user_id, review_note, decided_at, withdrawn_at,
                       withdraw_reason, handover_note, handover_at, created_at, version
                FROM adoption_application
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(mapRow(rs)) : Optional.empty(), id);
    }

    /** 撤回：仅 PENDING/APPROVED → WITHDRAWN，乐观锁。返回影响行数。 */
    public int withdraw(String id, String reason, int version) {
        return jdbcTemplate.update("""
                UPDATE adoption_application
                SET status = 'WITHDRAWN', withdraw_reason = ?, withdrawn_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                  AND status IN ('PENDING', 'APPROVED')
                """, reason, id, version);
    }

    /** 审核通过/拒绝：PENDING → APPROVED/REJECTED，乐观锁。返回影响行数。 */
    public int review(String id, String decision, String reason, String reviewerUserId, int version) {
        return jdbcTemplate.update("""
                UPDATE adoption_application
                SET status = ?, review_note = ?, reviewer_user_id = ?, decided_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND status = 'PENDING'
                """, decision, reason, reviewerUserId, id, version);
    }

    /** 交接完成：APPROVED → COMPLETED，乐观锁。返回影响行数。 */
    public int complete(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE adoption_application
                SET status = 'COMPLETED', handover_note = ?, handover_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND status = 'APPROVED'
                """, note, id, version);
    }

    /** 交接失败：APPROVED → CANCELLED，乐观锁。返回影响行数。 */
    public int cancelForHandoverFailure(String id, String note, int version) {
        return jdbcTemplate.update("""
                UPDATE adoption_application
                SET status = 'CANCELLED', handover_note = ?, handover_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND status = 'APPROVED'
                """, note, id, version);
    }

    /** 本人申请列表。 */
    public PageResult<AdoptionDtos.ApplicationView> findByApplicant(String applicantUserId,
                                                                     AdoptionDtos.MyApplicationQuery q) {
        StringBuilder where = new StringBuilder(" WHERE applicant_user_id = ? AND deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        args.add(applicantUserId);
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adoption_application" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<AdoptionDtos.ApplicationView> items = jdbcTemplate.query("""
                SELECT id FROM adoption_application %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where),
                (rs, rowNum) -> loadView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 管理员审核列表。 */
    public PageResult<AdoptionDtos.ApplicationView> findAdmin(AdoptionDtos.AdminApplicationQuery q) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getStatus())) {
            where.append(" AND status = ? ");
            args.add(q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND application_no LIKE ? ");
            args.add("%" + q.getKeyword().trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adoption_application" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<AdoptionDtos.ApplicationView> items = jdbcTemplate.query("""
                SELECT id FROM adoption_application %s ORDER BY created_at DESC LIMIT ? OFFSET ?
                """.formatted(where),
                (rs, rowNum) -> loadView(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 公开视图加载：组装申请视图，状态面板字段留空，由服务层填充。 */
    public AdoptionDtos.ApplicationView findView(String id) {
        return findById(id).map(this::toView).orElse(null);
    }

    private AdoptionDtos.ApplicationView loadView(String id) {
        return findById(id).map(this::toView).orElse(null);
    }

    private AdoptionDtos.ApplicationView toView(ApplicationRow row) {
        PetInfo pet = loadPet(row.petId());
        String applicantName = loadNickname(row.applicantUserId());
        String reviewerName = loadNickname(row.reviewerUserId());
        return new AdoptionDtos.ApplicationView(
                row.id(),
                row.applicationNo(),
                row.petId(),
                new AdoptionDtos.PetSummary(pet.id(), pet.name(), pet.species(), pet.breedName()),
                row.applicantUserId(),
                applicantName,
                row.statement(),
                row.profileSnapshot(),
                row.status(),
                null, null, null, null, null,
                row.reviewerUserId(),
                reviewerName,
                row.reviewNote(),
                row.decidedAt(),
                row.withdrawnAt(),
                row.withdrawReason(),
                row.handoverNote(),
                row.handoverAt(),
                row.createdAt(),
                row.version());
    }

    // ========== pet adoption_status 状态迁移（独立于 PetService.validateAdoptionTransition） ==========
    // 领养闭环驱动 pet.adoption_status 在 ADOPTABLE / ADOPTING / ADOPTED 之间迁移，
    // 与宠物管理后台的状态机命名（NOT_FOR_ADOPTION/AVAILABLE 等）刻意解耦：本模块
    // 只对符合领养入口语义的 ADOPTABLE 宠物变更状态，不触发 PetService 的转换校验。
    // 所有 UPDATE 均带 status 前置条件 + version 乐观锁，0 行返回交由 Service 判定
    // 版本冲突或状态非法。SELECT FOR UPDATE 由 Service 在事务内调用 lockPetForUpdate。

    /** 锁定 pet 行（SELECT ... FOR UPDATE），用于提交申请时阻塞并发双申请。返回当前状态与版本。 */
    public Optional<PetLockInfo> lockPetForUpdate(String petId) {
        return jdbcTemplate.query("""
                SELECT id, adoption_status, public_status, version, owner_user_id
                FROM pet
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """, rs -> rs.next() ? Optional.of(new PetLockInfo(
                rs.getString("id"), rs.getString("adoption_status"),
                rs.getString("public_status"), rs.getInt("version"),
                rs.getString("owner_user_id"))) : Optional.empty(), petId);
    }

    /** 宠物状态/归属快照（用于状态面板）。 */
    public record PetLockInfo(String id, String adoptionStatus, String publicStatus, int version,
                              String ownerUserId) {
    }

    /** 把 pet 从 ADOPTABLE 迁移到 ADOPTING（审核通过），乐观锁。 */
    public int transitionPetToAdopting(String petId, int version) {
        return jdbcTemplate.update("""
                UPDATE pet
                SET adoption_status = 'ADOPTING', info_updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND adoption_status = 'ADOPTABLE'
                """, petId, version);
    }

    /** 把 pet 从 ADOPTING/ADOPTABLE 迁移回 ADOPTABLE（拒绝/撤回/交接失败时回滚）。乐观锁。 */
    public int transitionPetBackToAdoptable(String petId, int version) {
        return jdbcTemplate.update("""
                UPDATE pet
                SET adoption_status = 'ADOPTABLE', info_updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                  AND adoption_status IN ('ADOPTING', 'ADOPTABLE')
                """, petId, version);
    }

    /** 把 pet 迁移到 ADOPTED 并把 owner_user_id 设为新主人（交接成功）。乐观锁。 */
    public int transitionPetToAdopted(String petId, String newOwnerUserId, int version) {
        return jdbcTemplate.update("""
                UPDATE pet
                SET adoption_status = 'ADOPTED', owner_user_id = ?, info_updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL AND adoption_status = 'ADOPTING'
                """, newOwnerUserId, petId, version);
    }

    /** 当前 pet 行的 adoption_status / version（用于回滚场景下读取最新版本）。 */
    public Optional<PetLockInfo> findPetInfo(String petId) {
        return jdbcTemplate.query("""
                SELECT id, adoption_status, public_status, version, owner_user_id
                FROM pet
                WHERE id = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(new PetLockInfo(
                rs.getString("id"), rs.getString("adoption_status"),
                rs.getString("public_status"), rs.getInt("version"),
                rs.getString("owner_user_id"))) : Optional.empty(), petId);
    }

    private record PetInfo(String id, String name, String species, String breedName) {
    }

    private PetInfo loadPet(String petId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT p.id, p.name, p.species, b.name AS breed_name
                    FROM pet p LEFT JOIN pet_breed b ON b.id = p.breed_id
                    WHERE p.id = ? AND p.deleted_at IS NULL
                    """, (rs, rowNum) -> new PetInfo(
                    rs.getString("id"), rs.getString("name"),
                    rs.getString("species"), rs.getString("breed_name")), petId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return new PetInfo(petId, null, null, null);
        }
    }

    private String loadNickname(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT nickname FROM sys_user WHERE id = ?", String.class, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private ApplicationRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp decided = rs.getTimestamp("decided_at");
        Timestamp withdrawn = rs.getTimestamp("withdrawn_at");
        Timestamp handover = rs.getTimestamp("handover_at");
        Timestamp created = rs.getTimestamp("created_at");
        return new ApplicationRow(
                rs.getString("id"),
                rs.getString("application_no"),
                rs.getString("pet_id"),
                rs.getString("applicant_user_id"),
                rs.getString("statement"),
                rs.getString("profile_snapshot"),
                rs.getString("status"),
                rs.getString("reviewer_user_id"),
                rs.getString("review_note"),
                decided == null ? null : decided.toInstant(),
                withdrawn == null ? null : withdrawn.toInstant(),
                rs.getString("withdraw_reason"),
                rs.getString("handover_note"),
                handover == null ? null : handover.toInstant(),
                created == null ? null : created.toInstant(),
                rs.getInt("version"));
    }

    // 以下为可领养宠物查询相关辅助方法，避免与 PetRepository 耦合重复 SQL —— 直接查 pet 表
    // 但仅返回 PUBLIC 且 ADOPTABLE 的宠物，遵循领养入口只展示符合条件的数据（REQ-ADOPT-001）。

    /** 可领养宠物列表（public_status='PUBLISHED' 且 adoption_status='ADOPTABLE'）。 */
    public PageResult<AdoptionDtos.AdoptablePetView> findAdoptablePets(AdoptionDtos.AdoptablePetQuery q) {
        StringBuilder where = new StringBuilder(
                " WHERE p.deleted_at IS NULL AND p.public_status = 'PUBLISHED' "
                        + "AND p.adoption_status = 'ADOPTABLE' ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(q.getKeyword())) {
            where.append(" AND p.name LIKE ? ");
            args.add("%" + q.getKeyword().trim() + "%");
        }
        if (StringUtils.hasText(q.getSpecies())) {
            where.append(" AND p.species = ? ");
            args.add(q.getSpecies().trim());
        }
        if (StringUtils.hasText(q.getBreedId())) {
            where.append(" AND p.breed_id = ? ");
            args.add(q.getBreedId().trim());
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pet p" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.getSize());
        pageArgs.add(q.offset());
        List<AdoptionDtos.AdoptablePetView> items = jdbcTemplate.query("""
                SELECT p.id FROM pet p %s ORDER BY p.info_updated_at DESC LIMIT ? OFFSET ?
                """.formatted(where),
                (rs, rowNum) -> loadAdoptablePet(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, q.getPage(), q.getSize(), total == null ? 0 : total);
    }

    /** 加载单条可领养宠物视图。 */
    public AdoptionDtos.AdoptablePetView findAdoptablePet(String petId) {
        return jdbcTemplate.query("""
                SELECT id FROM pet
                WHERE id = ? AND deleted_at IS NULL
                  AND public_status = 'PUBLISHED' AND adoption_status = 'ADOPTABLE'
                """, rs -> rs.next() ? loadAdoptablePet(rs.getString("id")) : null, petId);
    }

    private AdoptionDtos.AdoptablePetView loadAdoptablePet(String petId) {
        return jdbcTemplate.query("""
                SELECT p.id, p.name, p.species, p.breed_id, b.name AS breed_name, p.sex,
                       p.description, p.ownership_type, p.owner_user_id, p.public_status,
                       p.adoption_status, p.info_updated_at
                FROM pet p LEFT JOIN pet_breed b ON b.id = p.breed_id
                WHERE p.id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Timestamp infoUpdated = rs.getTimestamp("info_updated_at");
            String ownerUserId = rs.getString("owner_user_id");
            List<AdoptionDtos.PetImageRef> images = imagesOf(petId);
            return new AdoptionDtos.AdoptablePetView(
                    rs.getString("id"), rs.getString("name"), rs.getString("species"),
                    rs.getString("breed_id"), rs.getString("breed_name"), rs.getString("sex"),
                    rs.getString("description"), rs.getString("ownership_type"),
                    ownerUserId, rs.getString("public_status"), rs.getString("adoption_status"),
                    infoUpdated == null ? null : infoUpdated.toInstant(), images);
        }, petId);
    }

    private List<AdoptionDtos.PetImageRef> imagesOf(String petId) {
        return jdbcTemplate.query(
                "SELECT file_id, sort_order, cover_flag FROM pet_image WHERE pet_id = ? ORDER BY sort_order",
                (rs, rowNum) -> new AdoptionDtos.PetImageRef(
                        rs.getString("file_id"), rs.getInt("sort_order"),
                        rs.getBoolean("cover_flag"), "/api/v1/files/" + rs.getString("file_id")), petId);
    }
}
