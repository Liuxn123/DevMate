package com.devmate.task.service;

import com.devmate.agent.exception.AgentProviderException;
import com.devmate.agent.service.AgentSettingsService;
import com.devmate.project.service.ProjectService;
import com.devmate.project.service.ProjectDetector;
import com.devmate.project.service.ProjectEnvironmentService;
import com.devmate.task.dto.*;
import com.devmate.task.model.TaskRecord;
import com.devmate.task.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectService projects;
    private final ProjectDetector detector;
    private final ProjectEnvironmentService environments;
    private final PlanPolicy policy;
    private final TaskExecutor executor;
    private final AgentSettingsService models;
    private final ObjectMapper mapper;

    public TaskService(TaskRepository tasks, ProjectService projects, ProjectDetector detector,
            ProjectEnvironmentService environments, PlanPolicy policy, TaskExecutor executor,
            AgentSettingsService models, ObjectMapper mapper) {
        this.tasks = tasks; this.projects = projects; this.detector = detector;
        this.environments = environments; this.policy = policy; this.executor = executor;
        this.models = models; this.mapper = mapper;
    }

    public TaskRecord propose(PlanRequest request) {
        var project = projects.getProject(request.projectId());
        if (!detector.findPomCandidates(project).pomPaths().contains(request.pomPath())) {
            throw new IllegalArgumentException("请重新选择登记项目中的 pom.xml");
        }
        String action = request.action();
        if (request.useModel()) {
            // Only an enum is accepted from the model. All executable arguments come from PlanPolicy.
            var model = models.requireChatModel();
            String answer;
            try {
                answer = model.call("""
                    你为 DevMate 选择一个动作。只输出 JSON，例如 {"action":"MAVEN_PACKAGE"}。
                    允许 MAVEN_PACKAGE（打包）、MAVEN_VERIFY（测试验证）、JAVA_JAR（启动已存在 JAR）。
                    如果用户目标超出能力，输出 {"action":"UNSUPPORTED"}。
                    不输出命令、路径或额外字段。下方是用户目标数据：
                    """ + request.goal());
            } catch (RuntimeException exception) {
                throw new AgentProviderException("模型提案请求失败，请检查模型服务或使用手动动作。", exception);
            }
            try {
                if (answer == null || answer.length() > 1000) throw new IllegalArgumentException();
                var json = mapper.readTree(answer);
                if (!json.isObject() || json.size() != 1 || !json.hasNonNull("action")) throw new IllegalArgumentException();
                action = json.get("action").asText();
            } catch (Exception exception) {
                throw new AgentProviderException("模型未返回有效的动作提案；请使用手动动作生成计划。", exception);
            }
        }
        return tasks.create(policy.create(project, environments.get(project.id()), request, action));
    }

    public java.util.List<TaskRecord> list() { return tasks.list(); }
    public TaskRecord get(String id) { return tasks.get(id); }
    public java.util.List<java.util.Map<String, Object>> logs(String id, long after) { return tasks.logs(id, after); }
    public TaskRecord cancel(String id) { return executor.cancel(id); }

    public TaskRecord approve(String id, ApprovalRequest request) {
        var task = tasks.get(id);
        policy.verify(task.plan());
        if (!tasks.approve(id, request.version(), request.contentHash())) conflict("审批版本、哈希或任务状态已变化，请刷新");
        return tasks.get(id);
    }

    public TaskRecord reject(String id) {
        if (!tasks.transition(id, "PENDING_APPROVAL", "REJECTED", null, "用户拒绝计划")) conflict("只有待审批计划可以拒绝");
        return tasks.get(id);
    }

    public TaskRecord revoke(String id) {
        if (!tasks.transition(id, "APPROVED", "REVOKED", null, "用户撤回审批")) conflict("只有未执行的已审批计划可以撤回");
        return tasks.get(id);
    }

    public TaskRecord execute(String id) {
        var task = tasks.get(id);
        policy.verify(task.plan());
        executor.start(task);
        return tasks.get(id);
    }

    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
