package com.movierec.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movierec.backend.dto.RateMovieRequest;
import com.movierec.backend.dto.RatingDto;
import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.AuthCookieFactory;
import com.movierec.backend.security.AuthenticatedUser;
import com.movierec.backend.security.CustomUserDetailsService;
import com.movierec.backend.security.JwtAuthenticationFilter;
import com.movierec.backend.security.JwtService;
import com.movierec.backend.security.SecurityConfig;
import com.movierec.backend.service.RatingService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link RatingController}.
 */
@WebMvcTest(controllers = RatingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CustomUserDetailsService.class, AuthCookieFactory.class})
class RatingControllerTest {

    @TestConfiguration
    static class SecurityMockMvcTestConfig {
        @Bean
        MockMvcBuilderCustomizer securityMockMvcCustomizer() {
            return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RatingService ratingService;
    @MockitoBean private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static AuthenticatedUser authenticatedUserWithId(long id) {
        User user = User.builder()
                .id(id)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return new AuthenticatedUser(user);
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/movies/5/rating")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RateMovieRequest((short) 4))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usesUserIdFromJwtPrincipalNotAnyClientInput() throws Exception {
        when(ratingService.rateMovie(42L, 5L, (short) 4))
                .thenReturn(new RatingDto(5L, (short) 4, Instant.now()));

        mockMvc.perform(put("/api/movies/5/rating")
                        .with(user(authenticatedUserWithId(42L)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RateMovieRequest((short) 4))))
                .andExpect(status().isOk());

        verify(ratingService).rateMovie(eq(42L), eq(5L), eq((short) 4));
    }

    @Test
    void rejectsScoreBelowOne() throws Exception {
        mockMvc.perform(put("/api/movies/5/rating")
                        .with(user(authenticatedUserWithId(1L)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RateMovieRequest((short) 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsScoreAboveFive() throws Exception {
        mockMvc.perform(put("/api/movies/5/rating")
                        .with(user(authenticatedUserWithId(1L)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RateMovieRequest((short) 6))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingScore() throws Exception {
        mockMvc.perform(put("/api/movies/5/rating")
                        .with(user(authenticatedUserWithId(1L)))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
