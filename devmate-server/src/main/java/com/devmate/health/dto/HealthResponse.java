package com.devmate.health.dto;

/** The small JSON shape returned by the first learning endpoint. */
public record HealthResponse(String status, String application, String javaVersion) {
}