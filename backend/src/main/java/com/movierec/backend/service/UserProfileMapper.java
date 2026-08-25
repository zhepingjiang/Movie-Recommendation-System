package com.movierec.backend.service;

import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.User;
import com.movierec.backend.repository.UserGenreRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds the {@link UserProfileDto} returned by every profile-shaped endpoint (register, login,
 * profile read/update, avatar update, genre-preference update).
 *
 * <p>FIX: extracted out of {@code AuthService}/{@code UserService}, which each used to build this
 * DTO independently. {@code AuthService} hardcoded {@code preferredGenres} to an empty set, so a
 * returning user's real genre picks never showed up in the login response — a duplication bug.
 * Centralizing the mapping here means there is exactly one place that can get this wrong.
 *
 * <p>Must be called within an active transaction: it queries {@link UserGenreRepository}, and
 * {@code UserGenre.genre} is lazily fetched.
 */
@Component
@RequiredArgsConstructor
public class UserProfileMapper {

    private final UserGenreRepository userGenreRepository;

    public UserProfileDto toDto(User user) {
        Set<String> genreNames = userGenreRepository.findByIdUserId(user.getId()).stream()
                .map(userGenre -> userGenre.getGenre().getName())
                .collect(Collectors.toSet());
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getCreatedAt(),
                genreNames);
    }
}
