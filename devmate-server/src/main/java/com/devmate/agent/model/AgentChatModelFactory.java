package com.devmate.agent.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** 隔离 OpenAI 兼容适配器的客户端创建细节。 */
@Component
public class AgentChatModelFactory {

    public ChatModel create(AgentSettings settings) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(settings.baseUrl())
                .completionsPath("/chat/completions").apiKey(settings.apiKey()).build();
        return OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(settings.model()).build()).build();
    }
}
