package com.devmate.agent.exception;

public class AgentModelNotConfiguredException extends RuntimeException {

    public AgentModelNotConfiguredException() {
        super("Agent 模型尚未配置。请前往模型设置填写并保存 API 地址和 API Key。");
    }
}
