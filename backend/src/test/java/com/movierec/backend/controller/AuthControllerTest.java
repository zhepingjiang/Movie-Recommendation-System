package com.movierec.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movierec.backend.dto.LoginRequest;
import com.movierec.backend.dto.RegisterRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.entity.Role;
import com.movierec.backend.repository.UserRepository;
import com.movierec.backend.security.AuthCookieFactory;
import com.movierec.backend.security.CustomUserDetailsService;
import com.movierec.backend.security.JwtAuthenticationFilter;
import com.movierec.backend.security.JwtService;
import com.movierec.backend.security.SecurityConfig;
import com.movierec.backend.service.AuthService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link AuthController}: verifies the JWT never appears in the response body
 * (only in the {@code Set-Cookie} header, which must be {@code HttpOnly}) and that
 * {@code passwordHash} is never serialized.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, CustomUserDetailsService.class, AuthCookieFactory.class})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private AuthService authService;
    @MockitoBean private UserRepository userRepository;

    @Test
    void registerReturnsHttpOnlyCookieAndNoPasswordHash() throws Exception {
        UserProfileDto profile =
                new UserProfileDto(
                        1L, "bob", "bob@example.com", "bob", null, Role.USER, Instant.now(), Set.of());
        when(authService.register(any())).thenReturn(new AuthService.AuthResult(profile, "jwt-token"));

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(
                                        new RegisterRequest("bob", "bob@example.com", "password123", Set.of("Action")))))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().httpOnly("auth_token", true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jwt-token"))));
    }

    @Test
    void loginReturnsHttpOnlyCookieAndNoPasswordHash() throws Exception {
        UserProfileDto profile =
                new UserProfileDto(
                        1L, "alice", "alice@example.com", "alice", null, Role.USER, Instant.now(), Set.of());
        when(authService.login(any())).thenReturn(new AuthService.AuthResult(profile, "jwt-token"));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new LoginRequest("alice", "password123"))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("auth_token"))
                .andExpect(cookie().httpOnly("auth_token", true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jwt-token"))));
    }
}
