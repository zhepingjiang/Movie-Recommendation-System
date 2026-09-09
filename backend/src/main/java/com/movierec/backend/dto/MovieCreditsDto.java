package com.movierec.backend.dto;

import java.util.List;

/**
 * Director + top-billed cast for a single movie, flattened from {@link com.movierec.backend.entity.Movie}'s
 * {@code director} and {@code cast} associations into plain names.
 */
public record MovieCreditsDto(String director, List<String> cast) {}
