package com.devmate.agent.controller;

import com.devmate.agent.dto.AgentMessageResponse;
import com.devmate.agent.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent/conversations")
public class ConversationController {
    public record CreateRequest(@Size(max = 36) String projectId, @Size(max = 1000) String pomPath) {}
    public record MessageRequest(@NotBlank @Size(max = 4000) String message) {}
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service = service; }
    @GetMapping public List<Map<String, Object>> list() { return service.list(); }
    @PostMapping public Map<String, Object> create(@Valid @RequestBody CreateRequest request) { return service.create(request.projectId(), request.pomPath()); }
    @GetMapping("/{id}/messages") public List<Map<String, Object>> messages(@PathVariable String id) { return service.messages(id); }
    @PostMapping("/{id}/messages") public AgentMessageResponse send(@PathVariable String id, @Valid @RequestBody MessageRequest request) { return service.send(id, request.message()); }
}
