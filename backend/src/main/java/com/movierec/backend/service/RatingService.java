package com.movierec.backend.service;

import com.movierec.backend.dto.RatingDto;
import com.movierec.backend.entity.Movie;
import com.movierec.backend.entity.Rating;
import com.movierec.backend.entity.RatingId;
import com.movierec.backend.exception.MovieNotFoundException;
import com.movierec.backend.repository.MovieRepository;
import com.movierec.backend.repository.RatingRepository;
import com.movierec.backend.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a user's 1-5 star rating for a movie, backing
 * {@link com.movierec.backend.controller.RatingController}.
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    /**
     * Upserts the (user, movie) rating: a user may rate a movie at most once (see
     * {@link RatingId}, the entity's primary key), so re-rating updates the existing row's
     * score/updatedAt rather than failing on a PK conflict.
     */
    @Transactional
    public RatingDto rateMovie(Long userId, Long movieId, Short score) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        Instant now = Instant.now();

        Rating rating = ratingRepository
                .findById(new RatingId(userId, movieId))
                .orElseGet(() -> Rating.builder()
                        .id(new RatingId(userId, movieId))
                        .user(userRepository.getReferenceById(userId))
                        .movie(movie)
                        .createdAt(now)
                        .build());
        rating.setScore(score);
        rating.setUpdatedAt(now);
        Rating saved = ratingRepository.save(rating);

        return new RatingDto(saved.getId().getMovieId(), saved.getScore(), saved.getUpdatedAt());
    }
}
