package com.movierec.backend.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code Set-Cookie} header used to carry the JWT. The cookie is {@code HttpOnly} so
 * client-side JS can never read the token, and {@code SameSite=Lax} so it isn't attached to
 * cross-site {@code fetch}/XHR requests, which is why {@link SecurityConfig} can safely disable
 * CSRF protection without a separate token scheme.
 */
@Component
public class AuthCookieFactory {

    public static final String COOKIE_NAME = "auth_token";

    private final JwtService jwtService;
    private final boolean cookieSecure;

    public AuthCookieFactory(JwtService jwtService, @Value("${app.cookie-secure}") boolean cookieSecure) {
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    public ResponseCookie buildLoginCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMillis(jwtService.getExpirationMs()))
                .build();
    }

    public ResponseCookie buildLogoutCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
