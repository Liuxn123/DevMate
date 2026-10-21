package com.devmate.agent.dto;

public record AgentStatusResponse(boolean configured, String provider, String model, String message) {
}
