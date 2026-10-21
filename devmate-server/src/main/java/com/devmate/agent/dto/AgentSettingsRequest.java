package com.devmate.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentSettingsRequest(
        @NotBlank(message = "请输入 API 地址")
        @Size(max = 500, message = "API 地址长度无效")
        String baseUrl,
        @NotBlank(message = "请输入模型名称")
        @Size(max = 200, message = "模型名称长度无效")
        String model,
        @Size(max = 500, message = "API Key 长度无效")
        String apiKey,
        boolean clearApiKey) {
}
