package com.movierec.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Request body for {@code POST /api/auth/register}.
 *
 * <p>FIX: {@code genres} is new and required. Registration used to happen at the end of step 1
 * (account fields only); genre-picking was a separate step-2 call after the account already
 * existed. The onboarding flow now requires at least one genre pick to complete registration at
 * all, so the frontend collects both steps' data before calling this endpoint once, and no
 * partial (genre-less) account is ever created.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotEmpty Set<String> genres) {}
