package com.petspark.pet;

import com.petspark.common.api.PageResult;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
class PetRepository {
    private final JdbcTemplate jdbcTemplate;

    PetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    PageResult<PetView> findPets(PetQuery query, boolean admin, String ownerId) {
        StringBuilder where = new StringBuilder(" WHERE p.deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (!admin) {
            where.append(" AND (p.public_status = 'PUBLISHED' OR p.owner_user_id = ?) ");
            args.add(ownerId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            where.append(" AND p.name LIKE ? ");
            args.add("%" + query.getKeyword().trim() + "%");
        }
        if (StringUtils.hasText(query.getSpecies())) {
            where.append(" AND p.species = ? ");
            args.add(query.getSpecies().trim());
        }
        if (StringUtils.hasText(query.getBreedId())) {
            where.append(" AND p.breed_id = ? ");
            args.add(query.getBreedId().trim());
        }
        if (admin && StringUtils.hasText(query.getPublicStatus())) {
            where.append(" AND p.public_status = ? ");
            args.add(query.getPublicStatus().trim());
        }
        if (StringUtils.hasText(query.getAdoptionStatus())) {
            where.append(" AND p.adoption_status = ? ");
            args.add(query.getAdoptionStatus().trim());
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pet p" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(query.offset());
        List<PetView> items = jdbcTemplate.query("""
                SELECT p.id
                FROM pet p
                %s
                ORDER BY p.created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where), (rs, rowNum) -> toPet(rs.getString("id")), pageArgs.toArray());
        return new PageResult<>(items, query.getPage(), query.getSize(), total == null ? 0 : total);
    }

    Optional<PetView> findPet(String id) {
        return jdbcTemplate.query("SELECT id FROM pet WHERE id = ? AND deleted_at IS NULL",
                rs -> rs.next() ? Optional.of(toPet(rs.getString("id"))) : Optional.empty(), id);
    }

    void insertPet(String id, PetSaveRequest request, String ownerId, boolean admin) {
        jdbcTemplate.update("""
                INSERT INTO pet (id, name, species, breed_id, sex, birth_date, description, ownership_type,
                                 owner_user_id, adoption_status, boarding_status, public_status, info_updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, id, request.name(), request.species(), blankToNull(request.breedId()), valueOr(request.sex(), "UNKNOWN"),
                request.birthDate() == null ? null : Date.valueOf(request.birthDate()), request.description(),
                admin ? valueOr(request.ownershipType(), "PLATFORM") : "USER",
                admin ? blankToNull(request.ownerUserId()) : ownerId,
                valueOr(request.adoptionStatus(), "NOT_FOR_ADOPTION"), valueOr(request.boardingStatus(), "NONE"),
                admin ? valueOr(request.publicStatus(), "PRIVATE") : "PRIVATE");
    }

    int updatePet(String id, PetSaveRequest request) {
        return jdbcTemplate.update("""
                UPDATE pet
                SET name = ?, species = ?, breed_id = ?, sex = ?, birth_date = ?, description = ?,
                    ownership_type = ?, owner_user_id = ?, adoption_status = ?, boarding_status = ?,
                    public_status = ?, info_updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, request.name(), request.species(), blankToNull(request.breedId()), valueOr(request.sex(), "UNKNOWN"),
                request.birthDate() == null ? null : Date.valueOf(request.birthDate()), request.description(),
                valueOr(request.ownershipType(), "PLATFORM"), blankToNull(request.ownerUserId()),
                valueOr(request.adoptionStatus(), "NOT_FOR_ADOPTION"), valueOr(request.boardingStatus(), "NONE"),
                valueOr(request.publicStatus(), "PRIVATE"), id, request.version());
    }

    int updateStatus(String id, String adoptionStatus, String boardingStatus, String publicStatus, int version) {
        return jdbcTemplate.update("""
                UPDATE pet
                SET adoption_status = COALESCE(?, adoption_status),
                    boarding_status = COALESCE(?, boarding_status),
                    public_status = COALESCE(?, public_status),
                    info_updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, blankToNull(adoptionStatus), blankToNull(boardingStatus), blankToNull(publicStatus), id, version);
    }

    int softDelete(String id, int version) {
        return jdbcTemplate.update("UPDATE pet SET deleted_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ? AND version = ? AND deleted_at IS NULL", id, version);
    }

    List<BreedView> findBreeds(String species, String status) {
        StringBuilder where = new StringBuilder(" WHERE deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(species)) {
            where.append(" AND species = ? ");
            args.add(species.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ? ");
            args.add(status.trim());
        }
        return jdbcTemplate.query("SELECT id, species, name, description, status, version FROM pet_breed " + where + " ORDER BY species, name",
                (rs, rowNum) -> new BreedView(rs.getString("id"), rs.getString("species"), rs.getString("name"),
                        rs.getString("description"), rs.getString("status"), rs.getInt("version")), args.toArray());
    }

    Optional<BreedView> findBreed(String id) {
        return jdbcTemplate.query("SELECT id, species, name, description, status, version FROM pet_breed WHERE id = ? AND deleted_at IS NULL",
                rs -> rs.next() ? Optional.of(new BreedView(rs.getString("id"), rs.getString("species"), rs.getString("name"),
                        rs.getString("description"), rs.getString("status"), rs.getInt("version"))) : Optional.empty(), id);
    }

    boolean breedNameExists(String id, String species, String name) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pet_breed
                WHERE species = ? AND name = ? AND deleted_at IS NULL AND (? IS NULL OR id <> ?)
                """, Integer.class, species, name, id, id);
        return count != null && count > 0;
    }

    void insertBreed(String id, BreedRequest request) {
        jdbcTemplate.update("INSERT INTO pet_breed (id, species, name, description, status) VALUES (?, ?, ?, ?, ?)",
                id, request.species(), request.name(), request.description(), valueOr(request.status(), "ACTIVE"));
    }

    int updateBreed(String id, BreedRequest request) {
        return jdbcTemplate.update("""
                UPDATE pet_breed SET species = ?, name = ?, description = ?, status = ?, version = version + 1
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """, request.species(), request.name(), request.description(), valueOr(request.status(), "ACTIVE"), id, request.version());
    }

    void replaceImages(String petId, List<String> fileIds) {
        jdbcTemplate.update("DELETE FROM pet_image WHERE pet_id = ?", petId);
        if (fileIds == null) {
            return;
        }
        int order = 0;
        for (String fileId : fileIds.stream().filter(StringUtils::hasText).distinct().toList()) {
            jdbcTemplate.update("INSERT INTO pet_image (id, pet_id, file_id, sort_order, cover_flag) VALUES (?, ?, ?, ?, ?)",
                    java.util.UUID.randomUUID().toString(), petId, fileId, order, order == 0);
            order++;
        }
    }

    private PetView toPet(String id) {
        return jdbcTemplate.query("SELECT p.*, b.name AS breed_name FROM pet p LEFT JOIN pet_breed b ON b.id = p.breed_id WHERE p.id = ?",
                rs -> {
                    rs.next();
                    Date birthDate = rs.getDate("birth_date");
                    return new PetView(rs.getString("id"), rs.getString("name"), rs.getString("species"),
                            rs.getString("breed_id"), rs.getString("breed_name"), rs.getString("sex"),
                            birthDate == null ? null : birthDate.toLocalDate(), rs.getString("description"),
                            rs.getString("ownership_type"), rs.getString("owner_user_id"), rs.getString("adoption_status"),
                            rs.getString("boarding_status"), rs.getString("public_status"), rs.getInt("version"), images(id));
                }, id);
    }

    private List<PetImageView> images(String petId) {
        return jdbcTemplate.query("SELECT file_id, sort_order, cover_flag FROM pet_image WHERE pet_id = ? ORDER BY sort_order",
                (rs, rowNum) -> new PetImageView(rs.getString("file_id"), rs.getInt("sort_order"),
                        rs.getBoolean("cover_flag"), "/api/v1/files/" + rs.getString("file_id")), petId);
    }

    private String valueOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
