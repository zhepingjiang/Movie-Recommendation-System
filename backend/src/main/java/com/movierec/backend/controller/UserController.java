package com.movierec.backend.controller;

import com.movierec.backend.dto.UpdateGenrePreferencesRequest;
import com.movierec.backend.dto.UpdateProfileRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.exception.InvalidFileException;
import com.movierec.backend.security.AuthenticatedUser;
import com.movierec.backend.service.UserService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service profile endpoints for the authenticated user. All routes here require
 * authentication (enforced by {@link com.movierec.backend.security.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private final UserService userService;

    @GetMapping("/me")
    public UserProfileDto getCurrentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getProfile(principal.getId());
    }

    @PutMapping("/me")
    public UserProfileDto updateCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.getId(), request);
    }

    @PutMapping("/me/genres")
    public UserProfileDto updateGenrePreferences(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody UpdateGenrePreferencesRequest request) {
        return userService.updateGenrePreferences(principal.getId(), request);
    }

    @PostMapping("/me/avatar")
    public UserProfileDto uploadAvatar(
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("No file was uploaded");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(file.getContentType())) {
            throw new InvalidFileException("Avatar must be a JPEG, PNG, or WebP image");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new InvalidFileException("Avatar must be 5MB or smaller");
        }
        return userService.updateAvatar(principal.getId(), file);
    }
}
