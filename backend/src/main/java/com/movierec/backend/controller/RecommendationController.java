package com.movierec.backend.controller;

import com.movierec.backend.dto.MovieRecommendationDto;
import com.movierec.backend.security.AuthenticatedUser;
import com.movierec.backend.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cold-start recommendations, proxied to the Python recommendation service.
 *
 * <p><b>Security-sensitive.</b> FIX: the recommendation service's own endpoint takes a bare
 * {@code user_id} path parameter with no auth of its own — calling it directly from the browser
 * would let any signed-in user request any other user's personalized recommendations (IDOR). This
 * endpoint is the only sanctioned entry point: the user id always comes from the authenticated
 * JWT principal, never from client input, and the recommendation service is only ever called
 * server-to-server from here.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    // FIX: deliberately no @Validated on the class. Spring MVC validates @Min/@Max on
    // @RequestParam natively since Spring 6.1 and throws HandlerMethodValidationException, which
    // implements ErrorResponse and is handled by GlobalExceptionHandler's generic catch-all as a
    // proper 400. Adding @Validated routes it through the older AOP-proxy validation path instead,
    // which throws a raw ConstraintViolationException that GlobalExceptionHandler doesn't
    // recognize -- confirmed by test, that path surfaces as an opaque 500.
    @GetMapping("/cold-start")
    public List<MovieRecommendationDto> getColdStart(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return recommendationService.getColdStartRecommendations(principal.getId(), limit);
    }
}
