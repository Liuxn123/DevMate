package com.devmate.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentMessageRequest(
        // 限制输入长度，避免过大的用户消息占用过多模型上下文。
        @NotBlank(message = "问题不能为空")
        @Size(max = 4000, message = "问题不能超过 4000 个字符")
        String message,
        @Size(max = 36, message = "项目 ID 格式无效")
        String projectId,
        @Size(max = 1000, message = "pom.xml 相对路径过长")
        String pomPath) {
}
