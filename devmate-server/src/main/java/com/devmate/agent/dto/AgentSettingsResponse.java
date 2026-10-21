package com.devmate.agent.dto;

public record AgentSettingsResponse(
        String baseUrl,
        String model,
        boolean keyConfigured,
        boolean configured,
        String storageNote) {
}
