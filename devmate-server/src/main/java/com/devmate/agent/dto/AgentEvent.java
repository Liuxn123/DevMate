package com.devmate.agent.dto;

import java.util.Map;

public record AgentEvent(
        int sequence,
        int round,
        String type,
        String toolName,
        String summary,
        Map<String, Object> details) {
}
