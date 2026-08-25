package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.UpdateGenrePreferencesRequest;
import com.movierec.backend.dto.UpdateProfileRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.entity.UserGenre;
import com.movierec.backend.entity.UserGenreId;
import com.movierec.backend.exception.InvalidGenreSelectionException;
import com.movierec.backend.repository.GenreRepository;
import com.movierec.backend.repository.UserGenreRepository;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.storage.StorageService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private GenreRepository genreRepository;
    @Mock private UserGenreRepository userGenreRepository;
    @Mock private UserProfileMapper userProfileMapper;

    @InjectMocks private UserService userService;

    @Captor private ArgumentCaptor<List<UserGenre>> savedGenresCaptor;

    private User user;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(USER_ID)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .displayName("alice")
                .role(Role.USER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private static Genre genre(long id, String name) {
        return Genre.builder().id(id).name(name).isActive(true).build();
    }

    private static UserGenre existingPick(long userId, Genre genre, Instant createdAt) {
        return UserGenre.builder()
                .id(new UserGenreId(userId, genre.getId()))
                .genre(genre)
                .weight(BigDecimal.valueOf(1.0))
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    @Test
    void updateGenrePreferencesAddsNewlySelectedGenres() {
        Genre action = genre(1L, "Action");
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("Action"))).thenReturn(List.of(action));
        when(userGenreRepository.findByIdUserId(USER_ID)).thenReturn(List.of());

        userService.updateGenrePreferences(USER_ID, new UpdateGenrePreferencesRequest(Set.of("Action")));

        verify(userGenreRepository).deleteAll(List.of());
        verify(userGenreRepository).saveAll(savedGenresCaptor.capture());
        List<UserGenre> saved = savedGenresCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getGenre()).isEqualTo(action);
        assertThat(saved.get(0).getWeight()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(saved.get(0).getId()).isEqualTo(new UserGenreId(USER_ID, 1L));
    }

    @Test
    void updateGenrePreferencesRemovesDeselectedGenresButKeepsAtLeastOne() {
        Genre action = genre(1L, "Action");
        Genre comedy = genre(2L, "Comedy");
        UserGenre existingAction = existingPick(USER_ID, action, Instant.now());
        UserGenre existingComedy = existingPick(USER_ID, comedy, Instant.now());
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("Comedy"))).thenReturn(List.of(comedy));
        when(userGenreRepository.findByIdUserId(USER_ID)).thenReturn(List.of(existingAction, existingComedy));

        userService.updateGenrePreferences(USER_ID, new UpdateGenrePreferencesRequest(Set.of("Comedy")));

        verify(userGenreRepository).deleteAll(List.of(existingAction));
        verify(userGenreRepository).saveAll(List.of());
    }

    @Test
    void updateGenrePreferencesRejectsClearingAllGenres() {
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of())).thenReturn(List.of());

        assertThatThrownBy(
                        () -> userService.updateGenrePreferences(USER_ID, new UpdateGenrePreferencesRequest(Set.of())))
                .isInstanceOf(InvalidGenreSelectionException.class);

        verify(userGenreRepository, never()).findByIdUserId(any());
        verify(userGenreRepository, never()).deleteAll(any());
        verify(userGenreRepository, never()).saveAll(any());
    }

    @Test
    void updateGenrePreferencesPreservesUnchangedRowsInsteadOfRecreatingThem() {
        Genre action = genre(1L, "Action");
        Genre comedy = genre(2L, "Comedy");
        Instant originalCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        UserGenre existingAction = existingPick(USER_ID, action, originalCreatedAt);

        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("Action", "Comedy")))
                .thenReturn(List.of(action, comedy));
        when(userGenreRepository.findByIdUserId(USER_ID)).thenReturn(List.of(existingAction));

        userService.updateGenrePreferences(USER_ID, new UpdateGenrePreferencesRequest(Set.of("Action", "Comedy")));

        // Action was already selected: it must not be deleted, and must not be re-saved either.
        verify(userGenreRepository).deleteAll(List.of());
        verify(userGenreRepository).saveAll(savedGenresCaptor.capture());
        List<UserGenre> saved = savedGenresCaptor.getValue();
        assertThat(saved).extracting(UserGenre::getGenre).containsExactly(comedy);
    }

    @Test
    void updateGenrePreferencesIgnoresUnknownGenreNamesWhenAtLeastOneValidRemains() {
        Genre action = genre(1L, "Action");
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("Action", "NotAGenre"))).thenReturn(List.of(action));
        when(userGenreRepository.findByIdUserId(USER_ID)).thenReturn(List.of());

        userService.updateGenrePreferences(USER_ID, new UpdateGenrePreferencesRequest(Set.of("Action", "NotAGenre")));

        verify(userGenreRepository).saveAll(savedGenresCaptor.capture());
        assertThat(savedGenresCaptor.getValue()).extracting(UserGenre::getGenre).containsExactly(action);
    }

    @Test
    void updateGenrePreferencesRejectsWhenEverySubmittedNameIsUnknownOrInactive() {
        when(genreRepository.findByNameInAndIsActiveTrue(Set.of("NotAGenre"))).thenReturn(List.of());

        assertThatThrownBy(() -> userService.updateGenrePreferences(
                        USER_ID, new UpdateGenrePreferencesRequest(Set.of("NotAGenre"))))
                .isInstanceOf(InvalidGenreSelectionException.class);

        verify(userGenreRepository, never()).findByIdUserId(any());
    }

    @Test
    void updateGenrePreferencesRejectsNullGenres() {
        assertThatThrownBy(() -> userService.updateGenrePreferences(USER_ID, new UpdateGenrePreferencesRequest(null)))
                .isInstanceOf(InvalidGenreSelectionException.class);

        verify(genreRepository, never()).findByNameInAndIsActiveTrue(anyList());
        verify(userGenreRepository, never()).findByIdUserId(any());
    }

    @Test
    void getProfileReturnsCurrentGenrePicks() {
        // getProfile()'s DTO construction is entirely delegated to UserProfileMapper now (that's
        // the point of extracting it), so this only needs to verify UserService passes the right
        // user through and returns whatever the mapper produces -- not re-derive the mapping.
        UserProfileDto mapped = new UserProfileDto(
                USER_ID, "alice", "alice@example.com", "alice", null, Role.USER, Instant.now(), Set.of("Action"));
        when(userProfileMapper.toDto(user)).thenReturn(mapped);

        UserProfileDto profile = userService.getProfile(USER_ID);

        assertThat(profile.preferredGenres()).containsExactly("Action");
    }

    @Test
    void updateProfileDoesNotTouchGenrePicks() {
        userService.updateProfile(USER_ID, new UpdateProfileRequest("New Name"));

        verify(userGenreRepository, never()).saveAll(any());
        verify(userGenreRepository, never()).deleteAll(any());
    }
}
