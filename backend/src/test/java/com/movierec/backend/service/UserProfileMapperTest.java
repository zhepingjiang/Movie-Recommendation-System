package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Genre;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.entity.UserGenre;
import com.movierec.backend.entity.UserGenreId;
import com.movierec.backend.repository.UserGenreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileMapperTest {

    @Mock private UserGenreRepository userGenreRepository;

    @InjectMocks private UserProfileMapper userProfileMapper;

    @Test
    void mapsUsersCurrentGenrePicksByName() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .displayName("alice")
                .role(Role.USER)
                .createdAt(Instant.now())
                .build();
        Genre action = Genre.builder().id(1L).name("Action").isActive(true).build();
        Genre comedy = Genre.builder().id(2L).name("Comedy").isActive(true).build();
        UserGenre actionPick = UserGenre.builder()
                .id(new UserGenreId(1L, 1L))
                .genre(action)
                .weight(BigDecimal.ONE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        UserGenre comedyPick = UserGenre.builder()
                .id(new UserGenreId(1L, 2L))
                .genre(comedy)
                .weight(BigDecimal.ONE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(userGenreRepository.findByIdUserId(1L)).thenReturn(List.of(actionPick, comedyPick));

        UserProfileDto dto = userProfileMapper.toDto(user);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.preferredGenres()).containsExactlyInAnyOrder("Action", "Comedy");
    }

    @Test
    void returnsEmptySetWhenUserHasNoGenrePicks() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .displayName("alice")
                .role(Role.USER)
                .createdAt(Instant.now())
                .build();
        when(userGenreRepository.findByIdUserId(1L)).thenReturn(List.of());

        UserProfileDto dto = userProfileMapper.toDto(user);

        assertThat(dto.preferredGenres()).isEmpty();
    }
}
