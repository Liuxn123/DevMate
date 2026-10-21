package com.devmate.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlanRequest(
        @NotBlank @Size(max = 36) String projectId,
        @NotBlank @Size(max = 1000) String pomPath,
        @NotBlank @Size(max = 2000) String goal,
        @Size(max = 40) String action,
        @Size(max = 1000) String jarPath,
        boolean useModel) {
}
