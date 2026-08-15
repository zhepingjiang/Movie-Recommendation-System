package com.movierec.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movierec.backend.controller.AuthController;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms the {@code /api/admin/**} role check is actually enforced, even though no admin
 * controller exists yet — future admin endpoints (planned: movie upload/removal) will land
 * already protected.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CustomUserDetailsService.class, AuthCookieFactory.class})
class SecurityConfigTest {

    /**
     * Boot 4's {@code spring-boot-webmvc-test} module doesn't auto-apply
     * {@code SecurityMockMvcConfigurers.springSecurity()} the way older Boot versions did, so
     * without this, {@code @WithMockUser}'s context gets silently overwritten by
     * {@code SecurityContextHolderFilter} before {@code AuthorizationFilter} ever sees it.
     */
    @TestConfiguration
    static class SecurityMockMvcTestConfig {
        @Bean
        MockMvcBuilderCustomizer securityMockMvcCustomizer() {
            return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private UserRepository userRepository;

    @Test
    void unauthenticatedRequestToAdminRouteIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/movies")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminUserIsForbiddenFromAdminRoute() throws Exception {
        mockMvc.perform(get("/api/admin/movies")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUserPassesAuthorizationCheck() throws Exception {
        // No controller exists at this path yet, so authorization passes but routing 404s —
        // that 404 (not 401/403) is exactly what proves the role check let the request through.
        mockMvc.perform(get("/api/admin/movies")).andExpect(status().isNotFound());
    }
}
