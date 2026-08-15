package com.movierec.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/login}. {@code identifier} may be either the
 * user's username or email address.
 */
public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}
