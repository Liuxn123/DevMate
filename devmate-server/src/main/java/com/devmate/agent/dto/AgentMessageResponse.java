package com.devmate.agent.dto;

import java.util.List;

public record AgentMessageResponse(
        String answer,
        String model,
        String projectId,
        String status,
        int toolRounds,
        List<AgentEvent> events) {
}
