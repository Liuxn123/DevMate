package com.devmate.agent.model;

/** 后端内部的模型连接设置，包含密钥，不作为 HTTP 响应返回。 */
public record AgentSettings(String baseUrl, String model, String apiKey) {

    @Override
    public String toString() {
        return "AgentSettings[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=***]";
    }
}
