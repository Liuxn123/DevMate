package com.devmate.project.dto;

import java.util.List;

public record ProjectInspectionResponse(
        String projectId,
        String projectName,
        String rootPath,
        String pomPath,
        String inspectedAt,
        String status,
        String projectType,
        String javaVersionRequirement,
        boolean mavenWrapper,
        List<String> evidence,
        List<String> notices) {
}
