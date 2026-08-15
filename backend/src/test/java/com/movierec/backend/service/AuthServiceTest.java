package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.LoginRequest;
import com.movierec.backend.dto.RegisterRequest;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.exception.DuplicateUserException;
import com.movierec.backend.exception.InvalidCredentialsException;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser =
                User.builder()
                        .id(1L)
                        .username("alice")
                        .email("alice@example.com")
                        .passwordHash("hashed")
                        .displayName("alice")
                        .role(Role.USER)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
    }

    @Test
    void registerCreatesUserWithUserRoleAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("bob", "bob@example.com", "password123");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User u = invocation.getArgument(0);
                            u.setId(2L);
                            return u;
                        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthService.AuthResult result = authService.register(request);

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.profile().username()).isEqualTo("bob");
        assertThat(result.profile().role()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest("alice", "new@example.com", "password123");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("newname", "alice@example.com", "password123");
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void loginSucceedsWithCorrectPasswordByUsernameOrEmail() {
        LoginRequest request = new LoginRequest("alice@example.com", "correct-password");
        when(userRepository.findByUsernameOrEmail("alice@example.com", "alice@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.generateToken(existingUser)).thenReturn("jwt-token");

        AuthService.AuthResult result = authService.login(request);

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.profile().username()).isEqualTo("alice");
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest("alice", "wrong-password");
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownIdentifierWithSameGenericException() {
        LoginRequest request = new LoginRequest("nobody", "whatever");
        when(userRepository.findByUsernameOrEmail("nobody", "nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
    }
}
