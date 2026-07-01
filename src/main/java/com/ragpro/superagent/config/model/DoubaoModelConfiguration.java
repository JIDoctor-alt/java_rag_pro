package com.ragpro.superagent.config.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 豆包大模型：通过 OpenAI 兼容接口接入火山引擎 Ark。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "super-agent.models.providers.doubao", name = "enabled", havingValue = "true")
public class DoubaoModelConfiguration {

    @Bean("doubaoChatModel")
    public ChatModel doubaoChatModel(ModelProperties modelProperties) {
        var config = modelProperties.getProvider("doubao");
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("豆包已启用但未配置 API Key，请设置 DOUBAO_API_KEY");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .completionsPath(config.getCompletionsPath())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModel())
                .temperature(config.getTemperature())
                .build();

        log.info("Doubao model initialized: {}", config.getModel());
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
