package com.devmate.environment.model;

public record EnvironmentToolResult(
        String name,
        String status,
        String version,
        String path,
        String details) {
}
