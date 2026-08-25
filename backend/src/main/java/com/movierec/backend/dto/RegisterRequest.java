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
 *
 * <p>{@code fullName} is a display name only; the login {@code username} is generated server-side
 * from it (see {@link com.movierec.backend.service.AuthService#register}) since the frontend never
 * collects one directly.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotEmpty Set<String> genres) {}
