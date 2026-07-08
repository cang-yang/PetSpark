package com.petspark.pet;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.api.PageResult;
import com.petspark.common.security.AuthenticatedUser;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class PetController {
    private final PetService service;

    PetController(PetService service) {
        this.service = service;
    }

    @GetMapping("/pets")
    ApiResponse<PageResult<PetView>> pets(@Valid @ModelAttribute PetQuery query, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.publicPets(query, user.getId()));
    }

    @GetMapping("/pets/{id}")
    ApiResponse<PetView> pet(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getVisible(id, user.getId()));
    }

    @PostMapping("/pets/mine")
    ApiResponse<PetView> createMine(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PetSaveRequest request) {
        return ApiResponse.ok(service.createMine(user.getId(), request));
    }

    @PutMapping("/pets/mine/{id}")
    ApiResponse<PetView> updateMine(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @Valid @RequestBody PetSaveRequest request) {
        return ApiResponse.ok(service.updateMine(user.getId(), id, request));
    }

    @GetMapping("/breeds")
    ApiResponse<List<BreedView>> breeds(@RequestParam(required = false) String species, @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.breeds(species, status == null ? "ACTIVE" : status));
    }

    @GetMapping("/admin/pets")
    @RequirePermission("pet:manage")
    ApiResponse<PageResult<PetView>> adminPets(@Valid @ModelAttribute PetQuery query) {
        return ApiResponse.ok(service.adminPets(query));
    }

    @PostMapping("/admin/pets")
    @RequirePermission("pet:manage")
    ApiResponse<PetView> createAdmin(@Valid @RequestBody PetSaveRequest request) {
        return ApiResponse.ok(service.createAdmin(request));
    }

    @PutMapping("/admin/pets/{id}")
    @RequirePermission("pet:manage")
    ApiResponse<PetView> updateAdmin(@PathVariable String id, @Valid @RequestBody PetSaveRequest request) {
        return ApiResponse.ok(service.updateAdmin(id, request));
    }

    @PatchMapping("/admin/pets/{id}/status")
    @RequirePermission("pet:status")
    ApiResponse<PetView> updateStatus(@PathVariable String id, @Valid @RequestBody PetStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request));
    }

    @PostMapping("/admin/pets:batch-status")
    @RequirePermission("pet:status")
    ApiResponse<List<BatchPetStatusResult>> batchStatus(@Valid @RequestBody BatchPetStatusRequest request) {
        return ApiResponse.ok(service.batchStatus(request));
    }

    @DeleteMapping("/admin/pets/{id}")
    @RequirePermission("pet:manage")
    ApiResponse<Void> deleteAdmin(@PathVariable String id, @Valid @RequestBody DeletePetRequest request) {
        service.deleteAdmin(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/admin/breeds")
    @RequirePermission("breed:manage")
    ApiResponse<BreedView> createBreed(@Valid @RequestBody BreedRequest request) {
        return ApiResponse.ok(service.createBreed(request));
    }

    @PutMapping("/admin/breeds/{id}")
    @RequirePermission("breed:manage")
    ApiResponse<BreedView> updateBreed(@PathVariable String id, @Valid @RequestBody BreedRequest request) {
        return ApiResponse.ok(service.updateBreed(id, request));
    }
}
