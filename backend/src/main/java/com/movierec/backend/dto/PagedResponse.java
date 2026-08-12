package com.movierec.backend.dto;

import java.util.List;

/**
 * Generic envelope for a page of results returned by list endpoints, mirroring the
 * pagination metadata of Spring Data's {@code Page} without exposing that type directly.
 */
public record PagedResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {}
