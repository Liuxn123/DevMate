package com.devmate.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectEnvironment(
        @NotBlank @Size(max = 1000) String javaHome,
        @NotBlank @Size(max = 1000) String mavenHome,
        @NotBlank @Size(max = 1000) String cachePath) {
}
