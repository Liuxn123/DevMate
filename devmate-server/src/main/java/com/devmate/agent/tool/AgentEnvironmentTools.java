package com.devmate.agent.tool;

import java.util.Map;

import com.devmate.agent.trace.AgentTrace;
import com.devmate.environment.model.EnvironmentSnapshot;
import com.devmate.environment.service.EnvironmentScanner;

import org.springframework.ai.tool.annotation.Tool;

public final class AgentEnvironmentTools {

    private final EnvironmentScanner environmentScanner;
    private final AgentTrace trace;

    public AgentEnvironmentTools(EnvironmentScanner environmentScanner, AgentTrace trace) {
        this.environmentScanner = environmentScanner;
        this.trace = trace;
    }

    // 这是 Agent 当前能查询本机环境的唯一入口，实际探测逻辑复用已有扫描器。
    @Tool(name = "scan_environment", description = "只读检查本机已安装的 JDK、Maven 和 Git 版本及路径；不安装工具、不修改系统环境。用户询问本机开发工具或环境状态时调用。")
    public Object scanEnvironment() {
        String name = "scan_environment";
        if (!trace.beginToolCall(name, Map.of())) {
            return null;
        }
        try {
            EnvironmentSnapshot snapshot = environmentScanner.scan();
            trace.toolResult(name, snapshot);
            return snapshot;
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            trace.toolFailure(name, message);
            return Map.of("status", "ERROR", "message", message);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
