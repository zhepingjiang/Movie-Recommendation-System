package com.movierec.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/users/me}.
 */
public record UpdateProfileRequest(@Size(max = 150) String displayName) {}
