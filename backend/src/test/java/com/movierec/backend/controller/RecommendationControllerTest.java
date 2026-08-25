package com.movierec.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movierec.backend.entity.Role;
import com.movierec.backend.entity.User;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.AuthCookieFactory;
import com.movierec.backend.security.AuthenticatedUser;
import com.movierec.backend.security.CustomUserDetailsService;
import com.movierec.backend.security.JwtAuthenticationFilter;
import com.movierec.backend.security.JwtService;
import com.movierec.backend.security.SecurityConfig;
import com.movierec.backend.service.RecommendationService;
import java.time.Instant;
import java.util.List;
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
 * Slice test for {@link RecommendationController}. FIX target: the recommendation service's own
 * endpoint trusts a bare {@code user_id} path parameter with no auth of its own (IDOR) — this
 * controller must always source the user id from the authenticated JWT principal, and must never
 * accept it from client-supplied input (there is no such parameter on this endpoint at all).
 */
@WebMvcTest(controllers = RecommendationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CustomUserDetailsService.class, AuthCookieFactory.class})
class RecommendationControllerTest {

    @TestConfiguration
    static class SecurityMockMvcTestConfig {
        @Bean
        MockMvcBuilderCustomizer securityMockMvcCustomizer() {
            return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RecommendationService recommendationService;
    @MockitoBean private UserRepository userRepository;

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
        mockMvc.perform(get("/api/recommendations/cold-start")).andExpect(status().isUnauthorized());
    }

    @Test
    void usesUserIdFromJwtPrincipalNotAnyClientInput() throws Exception {
        when(recommendationService.getColdStartRecommendations(42L, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/cold-start").with(user(authenticatedUserWithId(42L))))
                .andExpect(status().isOk());

        // The endpoint has no user_id parameter at all -- this proves the id that reached the
        // service came from the authenticated principal, which is the only source available.
        verify(recommendationService).getColdStartRecommendations(eq(42L), eq(10));
    }

    @Test
    void defaultLimitIsTen() throws Exception {
        when(recommendationService.getColdStartRecommendations(1L, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/cold-start").with(user(authenticatedUserWithId(1L))))
                .andExpect(status().isOk());

        verify(recommendationService).getColdStartRecommendations(1L, 10);
    }

    @Test
    void rejectsLimitBelowOne() throws Exception {
        mockMvc.perform(get("/api/recommendations/cold-start")
                        .param("limit", "0")
                        .with(user(authenticatedUserWithId(1L))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsLimitAboveFifty() throws Exception {
        mockMvc.perform(get("/api/recommendations/cold-start")
                        .param("limit", "51")
                        .with(user(authenticatedUserWithId(1L))))
                .andExpect(status().isBadRequest());
    }
}
