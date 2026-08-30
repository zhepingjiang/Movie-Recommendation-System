package com.movierec.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.movierec.backend.dto.RatingDto;
import com.movierec.backend.entity.Movie;
import com.movierec.backend.entity.Rating;
import com.movierec.backend.entity.RatingId;
import com.movierec.backend.entity.User;
import com.movierec.backend.exception.MovieNotFoundException;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.RatingRepository;
import com.movierec.backend.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MOVIE_ID = 5L;

    @Mock private RatingRepository ratingRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private RatingService service;

    private static final Answer<Rating> RETURN_ARGUMENT = invocation -> invocation.getArgument(0);

    @Test
    void createsNewRatingWhenNoneExistsForThisUserAndMovie() {
        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(Movie.builder().id(MOVIE_ID).build()));
        when(ratingRepository.findById(new RatingId(USER_ID, MOVIE_ID))).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());
        when(ratingRepository.save(any(Rating.class))).then(RETURN_ARGUMENT);

        RatingDto result = service.rateMovie(USER_ID, MOVIE_ID, (short) 4);

        assertThat(result.movieId()).isEqualTo(MOVIE_ID);
        assertThat(result.score()).isEqualTo((short) 4);
        assertThat(result.updatedAt()).isNotNull();

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(captor.capture());
        Rating saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(new RatingId(USER_ID, MOVIE_ID));
        assertThat(saved.getScore()).isEqualTo((short) 4);
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
    }

    @Test
    void updatesScoreOnExistingRatingWithoutTouchingCreatedAt() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Rating existing = Rating.builder()
                .id(new RatingId(USER_ID, MOVIE_ID))
                .score((short) 2)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(Movie.builder().id(MOVIE_ID).build()));
        when(ratingRepository.findById(new RatingId(USER_ID, MOVIE_ID))).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any(Rating.class))).then(RETURN_ARGUMENT);

        RatingDto result = service.rateMovie(USER_ID, MOVIE_ID, (short) 5);

        assertThat(result.score()).isEqualTo((short) 5);
        assertThat(existing.getCreatedAt()).isEqualTo(createdAt);
        assertThat(existing.getUpdatedAt()).isAfter(createdAt);
        // Re-rating an existing row never needs a fresh association -- avoids an unnecessary
        // lookup for the common "user already rated this" path.
        verify(userRepository, never()).getReferenceById(anyLong());
    }

    @Test
    void throwsWhenMovieDoesNotExistAndNeverTouchesRatings() {
        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rateMovie(USER_ID, MOVIE_ID, (short) 3))
                .isInstanceOf(MovieNotFoundException.class);

        verify(ratingRepository, never()).findById(any());
        verify(ratingRepository, never()).save(any());
    }
}
