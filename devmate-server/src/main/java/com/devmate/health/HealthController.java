package com.devmate.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Maps an HTTP GET request to a Java method and returns an object Spring serializes as JSON. */
@RestController
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        // This reports the JVM running this backend process, not the shell's PATH Java.
        return new HealthResponse("UP", applicationName, System.getProperty("java.version"));
    }
}