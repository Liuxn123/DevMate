package com.devmate.agent.service;

import java.net.URI;
import java.util.List;

import com.devmate.agent.dto.AgentConnectionTestResponse;
import com.devmate.agent.dto.AgentSettingsRequest;
import com.devmate.agent.dto.AgentSettingsResponse;
import com.devmate.agent.exception.AgentModelNotConfiguredException;
import com.devmate.agent.model.AgentChatModelFactory;
import com.devmate.agent.model.AgentSettings;
import com.devmate.agent.repository.AgentSettingsRepository;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AgentSettingsService {

    private final AgentSettingsRepository settingsRepository;
    private final AgentChatModelFactory chatModelFactory;
    private final RestClient restClient;

    public AgentSettingsService(AgentSettingsRepository settingsRepository,
            AgentChatModelFactory chatModelFactory, RestClient.Builder restClientBuilder) {
        this.settingsRepository = settingsRepository;
        this.chatModelFactory = chatModelFactory;
        this.restClient = restClientBuilder.build();
    }

    public AgentSettingsResponse getSettings() {
        AgentSettings settings = settingsRepository.get();
        boolean hasKey = !settings.apiKey().isBlank();
        return new AgentSettingsResponse(settings.baseUrl(), settings.model(), hasKey, hasKey,
                "连接地址、模型和 API Key 保存在本机 SQLite 数据库中；API Key 不会返回给页面。");
    }

    public synchronized AgentSettingsResponse save(AgentSettingsRequest request) {
        AgentSettings previous = settingsRepository.get();
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String apiKey = request.clearApiKey() ? "" : clean(request.apiKey());
        if (!request.clearApiKey() && (apiKey == null || apiKey.isBlank())) {
            apiKey = previous.baseUrl().equals(baseUrl) ? previous.apiKey() : "";
        }
        settingsRepository.save(new AgentSettings(
                baseUrl, request.model().trim(), apiKey == null ? "" : apiKey));
        return getSettings();
    }

    public List<String> listModels(String baseUrl, String apiKey) {
        String cleanUrl = normalizeBaseUrl(baseUrl);
        String key = clean(apiKey);
        if (key == null || key.isBlank()) {
            AgentSettings saved = settingsRepository.get();
            if (!saved.baseUrl().equals(cleanUrl)) {
                throw new IllegalArgumentException("请输入该 API 地址对应的 API Key。");
            }
            key = saved.apiKey();
        }
        if (key.isBlank()) throw new IllegalArgumentException("请先输入 API Key。");
        String requestKey = key;
        JsonNode response = restClient.get().uri(URI.create(cleanUrl + "/models"))
                .headers(headers -> headers.setBearerAuth(requestKey))
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("data").isArray()) {
            throw new IllegalArgumentException("模型服务返回格式不正确；请确认地址兼容 OpenAI 的 /models 接口。");
        }
        return java.util.stream.StreamSupport.stream(response.path("data").spliterator(), false)
                .map(item -> item.path("id").asText("")).filter(id -> !id.isBlank()).sorted().toList();
    }

    public ChatModel requireChatModel() {
        AgentSettings settings = settingsRepository.get();
        if (settings.apiKey().isBlank()) throw new AgentModelNotConfiguredException();
        return chatModelFactory.create(settings);
    }

    public AgentConnectionTestResponse testConnection() {
        AgentSettings settings = settingsRepository.get();
        if (settings.apiKey().isBlank()) return new AgentConnectionTestResponse(false, "请先填写并保存 API Key。");
        try {
            String answer = chatModelFactory.create(settings).call(new Prompt("请只回复：连接成功"))
                    .getResult().getOutput().getText();
            if (answer == null || answer.isBlank()) return new AgentConnectionTestResponse(false, "模型服务已响应，但没有返回文本。请检查模型名称。");
            return new AgentConnectionTestResponse(true, "连接成功，模型已返回响应。");
        } catch (RuntimeException exception) {
            return new AgentConnectionTestResponse(false, "连接失败，请检查 API 地址、Key、模型名称和网络连接。");
        }
    }

    private static String normalizeBaseUrl(String value) {
        String url = clean(value);
        if (url == null || url.isBlank()) throw new IllegalArgumentException("请输入 API 地址。");
        url = url.replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException exception) { throw new IllegalArgumentException("API 地址格式无效。"); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("API 地址必须是有效的 HTTP 或 HTTPS 基础地址，不要包含密钥、查询参数或片段。");
        }
        return url;
    }

    private static String clean(String value) { return value == null ? null : value.trim(); }
}
