package com.devmate.project.controller;

import com.devmate.project.dto.ProjectEnvironment;
import com.devmate.project.service.ProjectEnvironmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{id}/environment")
public class ProjectEnvironmentController {
    private final ProjectEnvironmentService service;

    public ProjectEnvironmentController(ProjectEnvironmentService service) { this.service = service; }

    @GetMapping
    public ProjectEnvironment get(@PathVariable String id) { return service.get(id); }

    @PutMapping
    public ProjectEnvironment save(@PathVariable String id, @Valid @RequestBody ProjectEnvironment request) {
        return service.save(id, request);
    }
}
