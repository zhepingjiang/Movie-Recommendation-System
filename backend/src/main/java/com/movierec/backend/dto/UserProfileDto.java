package com.movierec.backend.dto;

import com.movierec.backend.entity.Role;
import java.time.Instant;
import java.util.Set;

/**
 * Read-only projection of a {@link com.movierec.backend.entity.User} returned by the API.
 * Deliberately excludes {@code passwordHash} — see that field's javadoc.
 */
public record UserProfileDto(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        Role role,
        Instant createdAt,
        Set<String> preferredGenres) {}
