package com.devmate.agent.service;

import com.devmate.agent.dto.AgentMessageRequest;
import com.devmate.agent.dto.AgentMessageResponse;
import com.devmate.agent.repository.ConversationRepository;
import com.devmate.project.service.ProjectService;
import com.devmate.project.service.ProjectDetector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.messages.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {
    private final ConversationRepository repository;
    private final AgentService agent;
    private final ProjectService projects;
    private final ProjectDetector detector;
    private final ObjectMapper mapper;
    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    public ConversationService(ConversationRepository repository, AgentService agent, ProjectService projects,
            ProjectDetector detector, ObjectMapper mapper) {
        this.repository = repository; this.agent = agent; this.projects = projects;
        this.detector = detector; this.mapper = mapper;
    }
    public List<Map<String, Object>> list() { return repository.list(); }
    public Map<String, Object> create(String projectId, String pomPath) {
        if (projectId != null && !projectId.isBlank()) {
            var project = projects.getProject(projectId);
            if (pomPath != null && !pomPath.isBlank() && !detector.findPomCandidates(project).pomPaths().contains(pomPath)) {
                throw new IllegalArgumentException("所选 pom.xml 不属于该项目");
            }
        } else if (pomPath != null && !pomPath.isBlank()) throw new IllegalArgumentException("需要先选择项目");
        return repository.create("新会话", projectId, pomPath);
    }

    public List<Map<String, Object>> messages(String id) { return repository.messages(id); }

    public AgentMessageResponse send(String id, String message) {
        if (!busy.add(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "该会话正在处理消息");
        try {
            var session = repository.get(id);
            var stored = repository.messages(id);
            if (stored.size() >= 200) throw new IllegalArgumentException("会话达到 100 轮上限，请新建会话");
            List<Message> history = new ArrayList<>();
            for (var entry : stored.subList(Math.max(0, stored.size() - 12), stored.size())) {
                String role = (String) entry.get("role");
                String payload = (String) entry.get("payload");
                if (role.equals("user")) history.add(new UserMessage(payload));
                else {
                    String answer = mapper.readTree(payload).path("answer").asText();
                    history.add(new AssistantMessage(answer.substring(0, Math.min(answer.length(), 8000))));
                }
            }
            var request = new AgentMessageRequest(message, (String) session.get("projectId"), (String) session.get("pomPath"));
            AgentMessageResponse response = agent.sendMessage(request, history);
            repository.appendPair(id, message, mapper.writeValueAsString(response));
            return response;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("会话数据无法读取", exception);
        } finally { busy.remove(id); }
    }
}
