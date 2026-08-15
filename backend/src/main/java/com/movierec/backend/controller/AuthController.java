package com.movierec.backend.controller;

import com.movierec.backend.dto.LoginRequest;
import com.movierec.backend.dto.RegisterRequest;
import com.movierec.backend.dto.UserProfileDto;
import com.movierec.backend.security.AuthCookieFactory;
import com.movierec.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and login. Neither endpoint returns the JWT in the response body — it's only ever
 * set as an {@code HttpOnly} cookie via {@link AuthCookieFactory}, so client-side JS never has
 * access to it.
 *
 * <p><b>Security-sensitive.</b>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookieFactory;

    @PostMapping("/register")
    public ResponseEntity<UserProfileDto> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.buildLoginCookie(result.token()).toString())
                .body(result.profile());
    }

    @PostMapping("/login")
    public ResponseEntity<UserProfileDto> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.buildLoginCookie(result.token()).toString())
                .body(result.profile());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.buildLogoutCookie().toString())
                .build();
    }
}
