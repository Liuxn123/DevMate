package com.devmate.project;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectRecord> listProjects() {
        return projectService.listProjects();
    }

    @GetMapping("/{id}")
    public ProjectRecord getProject(@PathVariable String id) {
        return projectService.getProject(id);
    }

    @PostMapping
    public ResponseEntity<ProjectRecord> registerProject(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectRecord project = projectService.registerLocalProject(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(project.id())
                .toUri();
        return ResponseEntity.created(location).body(project);
    }
}
