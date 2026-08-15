package com.movierec.backend.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple health-check endpoint used to verify the backend is up and reachable.
 * TODO: Real health check under K8s
 */
@RestController
public class HealthController {

    /**
     * @return a static {@code {"status": "ok"}} payload indicating the service is running
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
