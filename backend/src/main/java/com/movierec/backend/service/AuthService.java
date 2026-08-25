package com.movierec.backend.service;

import com.movierec.backend.dto.LoginRequest;
import com.movierec.backend.dto.RegisterRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.entity.UserGenre;
import com.movierec.backend.entity.UserGenreId;
import com.movierec.backend.exception.DuplicateUserException;
import com.movierec.backend.exception.InvalidCredentialsException;
import com.movierec.backend.exception.InvalidGenreSelectionException;
import com.movierec.backend.repository.GenreRepository;
import com.movierec.backend.repository.UserGenreRepository;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login. Issues the JWT returned to {@link com.movierec.backend.controller.AuthController},
 * which is responsible for placing it in the response cookie — this service never handles cookies
 * or HTTP directly.
 *
 * <p><b>Security-sensitive.</b>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // Matches the users.username column length (see User#username).
    private static final int USERNAME_MAX_LENGTH = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GenreRepository genreRepository;
    private final UserGenreRepository userGenreRepository;
    private final UserProfileMapper userProfileMapper;

    // FIX: registration now requires at least one genre pick (onboarding step 2 is no longer
    // optional/skippable), so the account and its initial genre picks are created atomically here
    // instead of via a separate PUT /api/users/me/genres call after the fact. If every submitted
    // name fails to resolve to a real, active genre, reject the whole registration rather than
    // silently creating a genre-less account.
    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("Email is already registered");
        }

        List<Genre> genres = genreRepository.findByNameInAndIsActiveTrue(request.genres());
        if (genres.isEmpty()) {
            throw new InvalidGenreSelectionException("Select at least one genre to complete registration");
        }

        Instant now = Instant.now();
        User user =
                User.builder()
                        .username(generateUniqueUsername(request.fullName()))
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .displayName(request.fullName())
                        .role(Role.USER)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
        user = userRepository.save(user);

        User savedUser = user;
        List<UserGenre> initialPicks = genres.stream()
                .map(genre -> UserGenre.builder()
                        .id(new UserGenreId(savedUser.getId(), genre.getId()))
                        .user(savedUser)
                        .genre(genre)
                        .weight(BigDecimal.ONE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                .toList();
        userGenreRepository.saveAll(initialPicks);

        return new AuthResult(toProfileDto(user), jwtService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public AuthResult login(LoginRequest request) {
        User user =
                userRepository
                        .findByUsernameOrEmail(request.identifier(), request.identifier())
                        .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username/email or password");
        }

        return new AuthResult(toProfileDto(user), jwtService.generateToken(user));
    }

    // FIX: used to hardcode preferredGenres to Set.of() here, independently of UserService's copy
    // of the same mapping — so a returning user's real genre picks never appeared in the login
    // response. Now delegates to the one shared mapper both services use.
    private UserProfileDto toProfileDto(User user) {
        return userProfileMapper.toDto(user);
    }

    // FIX: the frontend no longer collects a username at all -- registration only asks for a
    // full name, which becomes displayName verbatim. The login username is derived from it here
    // ("John Michael Smith" -> "john.michael.smith") with a numeric suffix appended on collision
    // ("john.smith", then "john.smith1", "john.smith2", ...), so every account still gets a
    // stable, unique handle to log in with.
    private String generateUniqueUsername(String fullName) {
        String base = Arrays.stream(fullName.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .map(part -> part.replaceAll("[^a-z0-9]", ""))
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("."));
        if (base.isEmpty()) {
            base = "user";
        }
        if (base.length() > USERNAME_MAX_LENGTH) {
            base = base.substring(0, USERNAME_MAX_LENGTH);
        }
        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        String candidateBase =
                base.length() > USERNAME_MAX_LENGTH - 3 ? base.substring(0, USERNAME_MAX_LENGTH - 3) : base;
        int suffix = 1;
        String candidate;
        do {
            candidate = candidateBase + suffix;
            suffix++;
        } while (userRepository.existsByUsername(candidate));
        return candidate;
    }

    /** Result of a successful register/login: the profile to return plus the JWT to cookie-ify. */
    public record AuthResult(UserProfileDto profile, String token) {}
}
