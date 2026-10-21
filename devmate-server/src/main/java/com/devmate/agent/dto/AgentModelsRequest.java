package com.devmate.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentModelsRequest(
        @NotBlank(message = "请输入 API 地址") @Size(max = 500) String baseUrl,
        @Size(max = 500) String apiKey) {
}
