package com.movierec.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, cookie-based JWT security setup.
 *
 * <p><b>Security-sensitive:</b> CSRF is disabled deliberately, not by oversight — the auth cookie
 * is {@code SameSite=Lax}, which browsers never attach to cross-site {@code fetch}/XHR requests
 * (only to top-level GET navigations), so the classic CSRF vector against our JSON API is already
 * closed without a separate CSRF-token scheme.
 *
 * <p>{@code /api/admin/**} is reserved and locked to {@code ROLE_ADMIN} now even though no
 * controller uses that prefix yet, so the enforcement exists before the first admin endpoint
 * (planned: movie upload/removal) does.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                // FIX: without this, Spring Security never adds its CorsFilter to the chain, so a
                // preflight OPTIONS request to any authenticated endpoint (anything outside
                // /api/auth/** or a permitAll GET) falls straight into anyRequest().authenticated()
                // and gets a 401 with no CORS headers -- the browser then blocks the real
                // PUT/POST/DELETE as a CORS failure before it's ever sent. Reuses WebConfig's
                // existing addCorsMappings policy (auto-detected via HandlerMappingIntrospector);
                // no separate CorsConfigurationSource bean needed.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        (request, response, authException) ->
                                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/error")
                                        .permitAll()
                                        .requestMatchers("/api/auth/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/movies/**",
                                                "/api/genres/**",
                                                "/api/trending/**")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
