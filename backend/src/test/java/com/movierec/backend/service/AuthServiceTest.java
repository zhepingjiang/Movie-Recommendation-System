package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.LoginRequest;
import com.movierec.backend.dto.RegisterRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.exception.DuplicateUserException;
import com.movierec.backend.exception.InvalidCredentialsException;
import com.movierec.backend.exception.InvalidGenreSelectionException;
import com.movierec.backend.repository.GenreRepository;
import com.movierec.backend.repository.UserGenreRepository;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GenreRepository genreRepository;
    @Mock private UserGenreRepository userGenreRepository;
    @Mock private UserProfileMapper userProfileMapper;

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

    private static Genre genre(long id, String name) {
        return Genre.builder().id(id).name(name).isActive(true).build();
    }

    @Test
    void registerCreatesUserWithUserRoleAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("Bob Jones", "bob@example.com", "password123", Set.of("Action"));
        Genre action = genre(1L, "Action");
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("bob.jones")).thenReturn(false);
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("Action"))).thenReturn(List.of(action));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User u = invocation.getArgument(0);
                            u.setId(2L);
                            return u;
                        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        UserProfileDto profile = new UserProfileDto(
                2L, "bob.jones", "bob@example.com", "Bob Jones", null, Role.USER, Instant.now(), Set.of("Action"));
        when(userProfileMapper.toDto(any(User.class))).thenReturn(profile);

        AuthService.AuthResult result = authService.register(request);

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.profile().username()).isEqualTo("bob.jones");
        assertThat(result.profile().role()).isEqualTo(Role.USER);
        assertThat(result.profile().preferredGenres()).containsExactly("Action");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("bob.jones");
        assertThat(savedUser.getValue().getDisplayName()).isEqualTo("Bob Jones");
    }

    @Test
    void registerAppendsIncrementingSuffixWhenGeneratedUsernameCollides() {
        RegisterRequest request = new RegisterRequest("Alice Doe", "alice2@example.com", "password123", Set.of("Action"));
        Genre action = genre(1L, "Action");
        when(userRepository.existsByEmail("alice2@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice.doe")).thenReturn(true);
        when(userRepository.existsByUsername("alice.doe1")).thenReturn(true);
        when(userRepository.existsByUsername("alice.doe2")).thenReturn(false);
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("Action"))).thenReturn(List.of(action));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        when(userProfileMapper.toDto(any(User.class)))
                .thenReturn(new UserProfileDto(
                        3L, "alice.doe2", "alice2@example.com", "Alice Doe", null, Role.USER, Instant.now(), Set.of("Action")));

        authService.register(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("alice.doe2");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("New Name", "alice@example.com", "password123", Set.of("Action"));
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void registerRejectsWhenNoSubmittedGenreNameResolves() {
        RegisterRequest request = new RegisterRequest("Bob Jones", "bob@example.com", "password123", Set.of("NotAGenre"));
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("NotAGenre"))).thenReturn(List.of());

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(InvalidGenreSelectionException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectPasswordByUsernameOrEmail() {
        LoginRequest request = new LoginRequest("alice@example.com", "correct-password");
        when(userRepository.findByUsernameOrEmail("alice@example.com", "alice@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.generateToken(existingUser)).thenReturn("jwt-token");
        UserProfileDto profile = new UserProfileDto(
                1L, "alice", "alice@example.com", "alice", null, Role.USER, Instant.now(), Set.of("Drama"));
        when(userProfileMapper.toDto(existingUser)).thenReturn(profile);

        AuthService.AuthResult result = authService.login(request);

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.profile().username()).isEqualTo("alice");
        // FIX target: a returning user's real genre picks must come back on login, not Set.of().
        assertThat(result.profile().preferredGenres()).containsExactly("Drama");
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
