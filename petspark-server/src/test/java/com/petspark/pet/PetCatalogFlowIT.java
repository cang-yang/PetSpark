package com.petspark.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.petspark.AbstractControllerTest;
import com.petspark.auth.JwtService;
import com.petspark.auth.SysUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class PetCatalogFlowIT extends AbstractControllerTest {
    private static final String ADMIN_ROLE = "00000000-0000-0000-0000-000000000102";
    private static final String USER_ROLE = "00000000-0000-0000-0000-000000000101";
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;
    private String adminId;
    private String ownerId;
    private String otherId;
    private String adminToken;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        adminId = user("pet_admin", ADMIN_ROLE);
        ownerId = user("pet_owner", USER_ROLE);
        otherId = user("pet_other", USER_ROLE);
        adminToken = token(adminId, "pet_admin");
        ownerToken = token(ownerId, "pet_owner");
        otherToken = token(otherId, "pet_other");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM pet_image WHERE pet_id IN (SELECT id FROM pet WHERE name LIKE 'IT-%')");
        jdbcTemplate.update("DELETE FROM pet WHERE name LIKE 'IT-%'");
        jdbcTemplate.update("DELETE FROM pet_breed WHERE name LIKE 'IT-%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE original_name LIKE 'it-pet-%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?, ?)", adminId, ownerId, otherId);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)", adminId, ownerId, otherId);
    }

    @Test
    void publicListPaginatesAndHidesPrivatePets() throws Exception {
        String breed = breed("IT-Corgi", "ACTIVE");
        pet("IT-Public", breed, ownerId, "PUBLISHED", "AVAILABLE");
        pet("IT-Private", breed, ownerId, "PRIVATE", "AVAILABLE");
        mockMvc.perform(get("/api/v1/pets").param("keyword", "IT-").param("page", "1").param("size", "10")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("IT-Public"));
    }

    @Test
    void ownerImageAndOwnershipRulesAreEnforced() throws Exception {
        String breed = breed("IT-Ragdoll", "ACTIVE");
        String ownImage = image(ownerId, "it-pet-own.png");
        String otherImage = image(otherId, "it-pet-other.png");
        mockMvc.perform(post("/api/v1/pets/mine").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"IT-Mine","species":"CAT","breedId":"%s","imageIds":["%s"]}
                                """.formatted(breed, ownImage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerUserId").value(ownerId));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pet_image WHERE file_id = ?", Integer.class, ownImage)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/pets/mine").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"IT-Bad","species":"CAT","breedId":"%s","imageIds":["%s"]}
                                """.formatted(breed, otherImage)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_OWNERSHIP_001"));
    }

    @Test
    void adminPermissionsBreedRulesStateMachineAndVersionConflictAreEnforced() throws Exception {
        mockMvc.perform(post("/api/v1/admin/breeds").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"species\":\"DOG\",\"name\":\"IT-Beagle\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/breeds").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"species\":\"DOG\",\"name\":\"IT-Beagle\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BREED_DUPLICATE_001"));

        String inactive = breed("IT-Inactive", "INACTIVE");
        mockMvc.perform(post("/api/v1/admin/pets").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT-NoBreed\",\"species\":\"DOG\",\"breedId\":\"" + inactive + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PET_BREED_001"));

        String active = breed("IT-Shiba", "ACTIVE");
        String petId = pet("IT-State", active, ownerId, "PUBLISHED", "AVAILABLE");
        mockMvc.perform(get("/api/v1/admin/pets").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/pets/{id}/status", petId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adoptionStatus\":\"ADOPTING\",\"reason\":\"review\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/pets/{id}/status", petId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adoptionStatus\":\"AVAILABLE\",\"reason\":\"stale\",\"version\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/pets/{id}/status", petId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adoptionStatus\":\"NOT_FOR_ADOPTION\",\"reason\":\"illegal\",\"version\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PET_STATE_001"));
    }

    private String user(String name, String role) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO sys_user (id, username, email, password_hash, nickname, status, token_version) VALUES (?, ?, ?, '$2a$10$test', ?, 'ACTIVE', 0)", id, name, name + "@example.com", name);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, role);
        return id;
    }
    private String breed(String name, String status) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO pet_breed (id, species, name, status) VALUES (?, 'DOG', ?, ?)", id, name, status);
        return id;
    }
    private String pet(String name, String breed, String owner, String visibility, String adoption) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO pet (id, name, species, breed_id, owner_user_id, ownership_type, adoption_status, boarding_status, public_status, info_updated_at) VALUES (?, ?, 'DOG', ?, ?, 'USER', ?, 'NONE', ?, CURRENT_TIMESTAMP(3))", id, name, breed, owner, adoption, visibility);
        return id;
    }
    private String image(String owner, String name) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO file_object (id, object_key, original_name, media_type, extension, size_bytes, sha256, status, owner_id, business_type, confirmed_at) VALUES (?, ?, ?, 'image/png', 'png', 12, REPEAT('a',64), 'ACTIVE', ?, 'PET_IMAGE', CURRENT_TIMESTAMP(3))", id, "it/" + id, name, owner);
        return id;
    }
    private String token(String id, String name) {
        return jwtService.issue(new SysUser(id, name, name + "@example.com", "$2a$10$test", name, "ACTIVE", 0), List.of()).value();
    }
    private String bearer(String token) { return "Bearer " + token; }
}
