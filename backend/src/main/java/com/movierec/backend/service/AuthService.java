package com.movierec.backend.service;

import com.movierec.backend.dto.LoginRequest;
import com.movierec.backend.dto.RegisterRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.exception.DuplicateUserException;
import com.movierec.backend.exception.InvalidCredentialsException;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.JwtService;
import java.time.Instant;
import java.util.Set;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("Email is already registered");
        }

        Instant now = Instant.now();
        User user =
                User.builder()
                        .username(request.username())
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .displayName(request.username())
                        .role(Role.USER)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
        user = userRepository.save(user);

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

    private UserProfileDto toProfileDto(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getCreatedAt(),
                Set.of());
    }

    /** Result of a successful register/login: the profile to return plus the JWT to cookie-ify. */
    public record AuthResult(UserProfileDto profile, String token) {}
}
