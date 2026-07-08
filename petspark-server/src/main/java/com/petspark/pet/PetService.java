package com.petspark.pet;

import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.file.FileObjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PetService {
    private final PetRepository pets;
    private final FileObjectRepository files;

    PetService(PetRepository pets, FileObjectRepository files) {
        this.pets = pets;
        this.files = files;
    }

    PageResult<PetView> publicPets(PetQuery query, String userId) {
        return pets.findPets(query, false, userId);
    }

    PageResult<PetView> adminPets(PetQuery query) {
        return pets.findPets(query, true, null);
    }

    PetView getVisible(String id, String userId) {
        PetView pet = get(id);
        if (!"PUBLISHED".equals(pet.publicStatus()) && !userId.equals(pet.ownerUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001);
        }
        return pet;
    }

    PetView get(String id) {
        return pets.findPet(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
    }

    List<BreedView> breeds(String species, String status) {
        return pets.findBreeds(species, status);
    }

    @Transactional
    BreedView createBreed(BreedRequest request) {
        ensureBreedUnique(null, request);
        String id = UUID.randomUUID().toString();
        pets.insertBreed(id, request);
        return pets.findBreed(id).orElseThrow();
    }

    @Transactional
    BreedView updateBreed(String id, BreedRequest request) {
        pets.findBreed(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
        ensureBreedUnique(id, request);
        if (request.version() == null || pets.updateBreed(id, request) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return pets.findBreed(id).orElseThrow();
    }

    @Transactional
    PetView createMine(String userId, PetSaveRequest request) {
        ensureBreedActive(request.breedId());
        ensureImagesOwned(userId, request.imageIds());
        String id = UUID.randomUUID().toString();
        pets.insertPet(id, request, userId, false);
        pets.replaceImages(id, request.imageIds());
        return get(id);
    }

    @Transactional
    PetView updateMine(String userId, String id, PetSaveRequest request) {
        PetView current = get(id);
        if (!userId.equals(current.ownerUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
        }
        ensureBreedActive(request.breedId());
        ensureImagesOwned(userId, request.imageIds());
        if (request.version() == null || pets.updatePet(id, request) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        pets.replaceImages(id, request.imageIds());
        return get(id);
    }

    @Transactional
    PetView createAdmin(PetSaveRequest request) {
        ensureBreedActive(request.breedId());
        ensureImagesAvailable(request.imageIds());
        String id = UUID.randomUUID().toString();
        pets.insertPet(id, request, null, true);
        pets.replaceImages(id, request.imageIds());
        return get(id);
    }

    @Transactional
    PetView updateAdmin(String id, PetSaveRequest request) {
        get(id);
        ensureBreedActive(request.breedId());
        ensureImagesAvailable(request.imageIds());
        if (request.version() == null || pets.updatePet(id, request) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        pets.replaceImages(id, request.imageIds());
        return get(id);
    }

    @Transactional
    PetView updateStatus(String id, PetStatusRequest request) {
        PetView current = get(id);
        validateAdoptionTransition(current.adoptionStatus(), request.adoptionStatus());
        if (pets.updateStatus(id, request.adoptionStatus(), request.boardingStatus(), request.publicStatus(), request.version()) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return get(id);
    }

    @Transactional
    List<BatchPetStatusResult> batchStatus(BatchPetStatusRequest request) {
        return request.items().stream().map(item -> {
            try {
                PetView pet = updateStatus(item.petId(), new PetStatusRequest(request.adoptionStatus(), request.boardingStatus(),
                        request.publicStatus(), request.reason(), item.version()));
                return new BatchPetStatusResult(item.petId(), true, "OK", "success", pet);
            } catch (BusinessException ex) {
                return new BatchPetStatusResult(item.petId(), false, ex.errorCode().code(), ex.getMessage(), null);
            }
        }).toList();
    }

    @Transactional
    void deleteAdmin(String id, DeletePetRequest request) {
        get(id);
        if (pets.softDelete(id, request.version()) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
    }

    private void ensureBreedUnique(String id, BreedRequest request) {
        if (pets.breedNameExists(id, request.species(), request.name())) {
            throw new BusinessException(ErrorCode.BREED_DUPLICATE_001);
        }
    }

    private void ensureBreedActive(String breedId) {
        if (breedId == null || breedId.isBlank()) {
            return;
        }
        BreedView breed = pets.findBreed(breedId).orElseThrow(() -> new BusinessException(ErrorCode.PET_BREED_001));
        if (!"ACTIVE".equals(breed.status())) {
            throw new BusinessException(ErrorCode.PET_BREED_001);
        }
    }

    private void ensureImagesOwned(String userId, List<String> imageIds) {
        if (imageIds == null) {
            return;
        }
        for (String imageId : imageIds) {
            if (!files.existsActiveOwned(imageId, userId)) {
                throw new BusinessException(ErrorCode.ACCESS_OWNERSHIP_001);
            }
        }
    }

    private void ensureImagesAvailable(List<String> imageIds) {
        if (imageIds == null) {
            return;
        }
        for (String imageId : imageIds) {
            if (!files.existsAvailable(imageId)) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
            }
        }
    }

    private void validateAdoptionTransition(String from, String to) {
        if (to == null || to.isBlank() || from.equals(to)) {
            return;
        }
        boolean legal = switch (from) {
            case "AVAILABLE" -> to.equals("ADOPTING") || to.equals("NOT_FOR_ADOPTION");
            case "ADOPTING" -> to.equals("AVAILABLE") || to.equals("ADOPTED");
            case "NOT_FOR_ADOPTION" -> to.equals("AVAILABLE");
            default -> false;
        };
        if (!legal) {
            throw new BusinessException(ErrorCode.PET_STATE_001);
        }
    }
}
