package com.devmate.environment.controller;

import com.devmate.environment.model.EnvironmentSnapshot;
import com.devmate.environment.service.EnvironmentScanner;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/environment")
public class EnvironmentController {

    private final EnvironmentScanner environmentScanner;

    public EnvironmentController(EnvironmentScanner environmentScanner) {
        this.environmentScanner = environmentScanner;
    }

    @GetMapping("/snapshot")
    public EnvironmentSnapshot getSnapshot() {
        return environmentScanner.scan();
    }

    @PostMapping("/scan")
    public EnvironmentSnapshot scan() {
        return environmentScanner.scan();
    }
}
