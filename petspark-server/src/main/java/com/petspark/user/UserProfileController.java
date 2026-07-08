package com.petspark.user;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<UserProfileView> getMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(service.getMine(user.getId()));
    }

    @PutMapping
    public ApiResponse<UserProfileView> updateMine(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.ok(service.updateMine(user.getId(), request));
    }
}
