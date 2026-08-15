package com.movierec.backend.entity;

/**
 * Authorization role assigned to a {@link User}. Determines which {@code /api/**}
 * endpoints the user's JWT grants access to (see SecurityConfig).
 */
public enum Role {
    USER,
    ADMIN
}
