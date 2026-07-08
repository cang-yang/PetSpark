package com.petspark.user;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.file.FileObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserProfileService {

    private final UserProfileRepository repository;
    private final FileObjectRepository fileRepository;
    private final PhoneCrypto phoneCrypto;

    public UserProfileService(UserProfileRepository repository,
            FileObjectRepository fileRepository,
            PhoneCrypto phoneCrypto) {
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.phoneCrypto = phoneCrypto;
    }

    public UserProfileView getMine(String userId) {
        return view(load(userId));
    }

    @Transactional
    public UserProfileView updateMine(String userId, UpdateUserProfileRequest request) {
        UserProfile current = load(userId);
        String nickname = normalizeNickname(request.nickname(), current.nickname());
        String phoneCiphertext = normalizePhone(request.phone(), current.phoneCiphertext());
        String avatarFileId = normalizeAvatar(request.avatarFileId(), current.avatarFileId(), userId);
        String bio = normalizeBio(request.bio(), current.bio());
        int updated = repository.update(userId, request.version(), nickname, phoneCiphertext, avatarFileId, bio);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT_001);
        }
        return getMine(userId);
    }

    private UserProfile load(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_001));
    }

    private UserProfileView view(UserProfile profile) {
        String avatarUrl = StringUtils.hasText(profile.avatarFileId())
                ? "/api/v1/files/" + profile.avatarFileId()
                : null;
        return new UserProfileView(
                profile.id(),
                profile.username(),
                profile.email(),
                profile.nickname(),
                profile.avatarFileId(),
                avatarUrl,
                phoneCrypto.mask(profile.phoneCiphertext()),
                profile.bio(),
                profile.version(),
                profile.updatedAt());
    }

    private String normalizeNickname(String nickname, String current) {
        if (nickname == null) {
            return current;
        }
        String trimmed = nickname.trim();
        if (!StringUtils.hasText(trimmed) || trimmed.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return trimmed;
    }

    private String normalizePhone(String phone, String current) {
        if (phone == null) {
            return current;
        }
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.matches("^\\+?[0-9]{7,15}$")) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        return phoneCrypto.encrypt(trimmed);
    }

    private String normalizeAvatar(String avatarFileId, String current, String userId) {
        if (avatarFileId == null) {
            return current;
        }
        String trimmed = avatarFileId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!fileRepository.existsAvailable(trimmed)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND_001);
        }
        String ownerStatus = fileRepository.findStatusForOwner(trimmed, userId).orElse(null);
        if (ownerStatus == null) {
            throw new org.springframework.security.access.AccessDeniedException("avatar owner required");
        }
        if (!"ACTIVE".equals(ownerStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_001);
        }
        return trimmed;
    }

    private String normalizeBio(String bio, String current) {
        if (bio == null) {
            return current;
        }
        String trimmed = bio.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
