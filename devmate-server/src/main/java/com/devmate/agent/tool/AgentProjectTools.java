package com.devmate.agent.tool;

import java.util.Map;

import com.devmate.agent.trace.AgentTrace;
import com.devmate.project.dto.ProjectInspectionResponse;
import com.devmate.project.model.ProjectRecord;
import com.devmate.project.service.ProjectDetector;

import org.springframework.ai.tool.annotation.Tool;

public final class AgentProjectTools {

    // 项目和 pom 路径由后端绑定，而不是让模型在调用参数里指定任意文件。
    private final ProjectRecord project;
    private final String pomPath;
    private final ProjectDetector projectDetector;
    private final AgentTrace trace;

    public AgentProjectTools(ProjectRecord project, String pomPath, ProjectDetector projectDetector, AgentTrace trace) {
        this.project = project;
        this.pomPath = pomPath;
        this.projectDetector = projectDetector;
        this.trace = trace;
    }

    // @Tool 把这个只读方法声明为模型可见的能力；是否可用仍由 AgentService 决定。
    @Tool(name = "inspect_project", description = "只读检查用户当前选定项目及其已选择的 Maven pom.xml，返回技术栈、Java 版本线索和识别证据；不运行 Maven 或项目脚本、不修改项目。仅在回答项目结构或 pom 配置问题时调用。")
    public ProjectInspectionResponse inspectProject() {
        String name = "inspect_project";
        Map<String, Object> arguments = Map.of("projectId", project.id(), "pomPath", pomPath);
        if (!trace.beginToolCall(name, arguments)) {
            return null;
        }
        try {
            // 直接调用只读识别器，不走会更新项目记录的管理服务。
            ProjectInspectionResponse inspection = projectDetector.inspect(project, pomPath);
            trace.toolResult(name, inspection);
            return inspection;
        } catch (RuntimeException exception) {
            String message = safeMessage(exception);
            trace.toolFailure(name, message);
            return new ProjectInspectionResponse(project.id(), project.name(), project.rootPath(), pomPath,
                    java.time.Instant.now().toString(), "ERROR", "UNKNOWN", null, false,
                    java.util.List.of(), java.util.List.of(message));
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
