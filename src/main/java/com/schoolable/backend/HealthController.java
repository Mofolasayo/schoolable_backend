package com.schoolable.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping({"/up", "/health"})
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
