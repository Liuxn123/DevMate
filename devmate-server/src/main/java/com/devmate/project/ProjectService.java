package com.devmate.project;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<ProjectRecord> listProjects() {
        return projectRepository.findAll();
    }

    public ProjectRecord getProject(String id) {
        return projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public ProjectRecord registerLocalProject(ProjectCreateRequest request) {
        String normalizedPath;
        try {
            normalizedPath = Path.of(request.rootPath().trim()).toAbsolutePath().normalize().toString();
        } catch (InvalidPathException exception) {
            throw new InvalidProjectPathException("项目路径格式无效");
        }

        if (projectRepository.findByRootPath(normalizedPath).isPresent()) {
            throw new ProjectAlreadyRegisteredException(normalizedPath);
        }

        ProjectRecord project = new ProjectRecord(
                UUID.randomUUID().toString(),
                request.name().trim(),
                "LOCAL",
                null,
                normalizedPath,
                "UNKNOWN",
                Instant.now().toString());

        try {
            projectRepository.save(project);
        } catch (DataIntegrityViolationException exception) {
            // The unique index is the final guard if two requests race past the lookup above.
            throw new ProjectAlreadyRegisteredException(normalizedPath);
        }
        return project;
    }
}
