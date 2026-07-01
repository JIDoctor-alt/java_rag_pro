package com.ragpro.lovemaster.config;

import com.ragpro.lovemaster.advisor.LoveSensitiveAdvisor;
import com.ragpro.lovemaster.advisor.ReReadingAdvisor;
import com.ragpro.superagent.advisor.LoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoveMasterConfiguration {

    @Bean("loveMasterChatMemory")
    public ChatMemory loveMasterChatMemory(
            JdbcChatMemoryRepository chatMemoryRepository,
            LoveMasterProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(properties.getMaxMemoryMessages())
                .build();
    }

    @Bean("loveMasterChatClient")
    public ChatClient loveMasterChatClient(
            ChatModel chatModel,
            LoveMasterProperties properties,
            @Qualifier("loveMasterChatMemory") ChatMemory loveMasterChatMemory,
            ReReadingAdvisor reReadingAdvisor,
            LoveSensitiveAdvisor loveSensitiveAdvisor,
            LoggingAdvisor loggingAdvisor) {

        return ChatClient.builder(chatModel)
                .defaultSystem(properties.getSystemPrompt())
                .defaultAdvisors(
                        loveSensitiveAdvisor,
                        reReadingAdvisor,
                        MessageChatMemoryAdvisor.builder(loveMasterChatMemory).build(),
                        loggingAdvisor
                )
                .build();
    }
}
