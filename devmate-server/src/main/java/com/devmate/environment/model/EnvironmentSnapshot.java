package com.devmate.environment.model;

import java.util.List;

public record EnvironmentSnapshot(
        String scannedAt,
        List<EnvironmentToolResult> tools,
        String safetyNote) {
}
