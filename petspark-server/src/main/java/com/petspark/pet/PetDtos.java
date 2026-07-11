package com.petspark.pet;

import com.petspark.common.api.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

class PetQuery extends PageQuery {
    private String keyword;
    private String species;
    private String breedId;
    private String publicStatus;
    private String adoptionStatus;
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getBreedId() { return breedId; }
    public void setBreedId(String breedId) { this.breedId = breedId; }
    public String getPublicStatus() { return publicStatus; }
    public void setPublicStatus(String publicStatus) { this.publicStatus = publicStatus; }
    public String getAdoptionStatus() { return adoptionStatus; }
    public void setAdoptionStatus(String adoptionStatus) { this.adoptionStatus = adoptionStatus; }
}

record BreedView(String id, String species, String name, String description, String status, int version) {}
record PetImageView(String fileId, int sortOrder, boolean coverFlag, String previewUrl) {}
record PetView(String id, String name, String species, String breedId, String breedName, String sex,
               LocalDate birthDate, String description, String ownershipType, String ownerUserId,
               String adoptionStatus, String boardingStatus, String publicStatus, int version,
               String color, String behaviorTraits, String sterilizationStatus, String trainingLevel,
               String specialNeeds, LocalDate registeredAt, boolean ownedByCurrentUser,
               List<PetImageView> images) {}
record BatchPetStatusResult(String petId, boolean success, String code, String message, PetView pet) {}
record BreedRequest(@NotBlank @Size(max = 32) String species, @NotBlank @Size(max = 64) String name,
                    @Size(max = 500) String description, String status, Integer version) {}
record PetSaveRequest(@NotBlank @Size(max = 64) String name, @NotBlank @Size(max = 32) String species,
                      String breedId, String sex, LocalDate birthDate, @Size(max = 1000) String description,
                      String ownershipType, String ownerUserId, String adoptionStatus, String boardingStatus,
                      String publicStatus, Integer version, List<String> imageIds,
                      @Size(max = 64) String color, @Size(max = 500) String behaviorTraits,
                      @Pattern(regexp = "UNKNOWN|STERILIZED|INTACT") String sterilizationStatus,
                      @Pattern(regexp = "UNASSESSED|BASIC|INTERMEDIATE|ADVANCED") String trainingLevel,
                      @Size(max = 1000) String specialNeeds, LocalDate registeredAt) {}
record PetStatusRequest(String adoptionStatus, String boardingStatus, String publicStatus,
                        @NotBlank String reason, @NotNull Integer version) {}
record DeletePetRequest(@NotNull Integer version) {}
record BatchPetStatusItem(@NotBlank String petId, @NotNull Integer version) {}
record BatchPetStatusRequest(@Valid @NotNull List<BatchPetStatusItem> items, String adoptionStatus,
                             String boardingStatus, String publicStatus, @NotBlank String reason) {}
