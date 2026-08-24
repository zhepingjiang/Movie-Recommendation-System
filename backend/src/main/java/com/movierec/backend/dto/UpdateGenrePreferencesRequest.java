package com.movierec.backend.dto;

import java.util.Set;

/**
 * Request body for {@code PUT /api/users/me/genres}. Unknown or inactive genre names are
 * silently ignored — the frontend only ever offers names from {@code GET /api/genres}.
 */
public record UpdateGenrePreferencesRequest(Set<String> genres) {}
