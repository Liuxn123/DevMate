package com.devmate.project.service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devmate.project.dto.PomCandidatesResponse;
import com.devmate.project.dto.ProjectCreateRequest;
import com.devmate.project.dto.ProjectInspectionResponse;
import com.devmate.project.exception.InvalidProjectPathException;
import com.devmate.project.exception.ProjectAlreadyRegisteredException;
import com.devmate.project.exception.ProjectNotFoundException;
import com.devmate.project.model.ProjectRecord;
import com.devmate.project.repository.ProjectRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectDetector projectDetector;

    public ProjectService(ProjectRepository projectRepository, ProjectDetector projectDetector) {
        this.projectRepository = projectRepository;
        this.projectDetector = projectDetector;
    }

    public List<ProjectRecord> listProjects() {
        return projectRepository.findAll();
    }

    public ProjectRecord getProject(String id) {
        return projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public PomCandidatesResponse findPomCandidates(String id) {
        return projectDetector.findPomCandidates(getProject(id));
    }

    public ProjectInspectionResponse inspectProject(String id, String pomPath) {
        ProjectRecord project = getProject(id);
        ProjectInspectionResponse inspection = projectDetector.inspect(project, pomPath);
        if (!inspection.projectType().equals(project.projectType())) {
            projectRepository.updateProjectType(project.id(), inspection.projectType());
        }
        return inspection;
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
