package com.devmate.agent.controller;

import com.devmate.agent.dto.AgentConnectionTestResponse;
import com.devmate.agent.dto.AgentMessageRequest;
import com.devmate.agent.dto.AgentMessageResponse;
import com.devmate.agent.dto.AgentModelsRequest;
import com.devmate.agent.dto.AgentSettingsRequest;
import com.devmate.agent.dto.AgentSettingsResponse;
import com.devmate.agent.dto.AgentStatusResponse;
import com.devmate.agent.service.AgentService;
import com.devmate.agent.service.AgentSettingsService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentService agentService;
    private final AgentSettingsService settingsService;

    public AgentController(AgentService agentService, AgentSettingsService settingsService) {
        this.agentService = agentService;
        this.settingsService = settingsService;
    }

    @GetMapping("/status")
    public AgentStatusResponse getStatus() {
        return agentService.getStatus();
    }

    @GetMapping("/settings")
    public AgentSettingsResponse getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping("/settings")
    public AgentSettingsResponse saveSettings(@Valid @RequestBody AgentSettingsRequest request) {
        return settingsService.save(request);
    }

    @PostMapping("/models")
    public java.util.List<String> listModels(@Valid @RequestBody AgentModelsRequest request) {
        return settingsService.listModels(request.baseUrl(), request.apiKey());
    }

    @PostMapping("/settings/test")
    public AgentConnectionTestResponse testConnection() {
        return settingsService.testConnection();
    }

    // HTTP 层负责接收并校验请求；Agent 的决策循环放在 Service，避免控制器承担业务流程。
    @PostMapping("/messages")
    public AgentMessageResponse sendMessage(@Valid @RequestBody AgentMessageRequest request) {
        return agentService.sendMessage(request);
    }
}
