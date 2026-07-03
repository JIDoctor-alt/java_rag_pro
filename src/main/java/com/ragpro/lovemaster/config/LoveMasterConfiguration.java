package com.ragpro.lovemaster.config;

import com.ragpro.lovemaster.advisor.LoveSensitiveAdvisor;
import com.ragpro.lovemaster.advisor.ReReadingAdvisor;
import com.ragpro.lovemaster.rag.LoveCourseRecommendService;
import com.ragpro.superagent.advisor.LoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoveMasterConfiguration {

    @Bean
    public ToolCallbackProvider loveMasterTools(LoveCourseRecommendService courseRecommendService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(courseRecommendService)
                .build();
    }

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
            VectorStore vectorStore,
            ToolCallbackProvider loveMasterTools,
            @Qualifier("loveMasterChatMemory") ChatMemory loveMasterChatMemory,
            ReReadingAdvisor reReadingAdvisor,
            LoveSensitiveAdvisor loveSensitiveAdvisor,
            LoggingAdvisor loggingAdvisor) {

        var builder = ChatClient.builder(chatModel)
                .defaultSystem(properties.getSystemPrompt())
                .defaultToolCallbacks(loveMasterTools);

        if (properties.getRag().isEnabled() && "local".equalsIgnoreCase(properties.getRag().getSource())) {
            var rag = properties.getRag();
            SearchRequest searchRequest = SearchRequest.builder()
                    .topK(rag.getTopK())
                    .similarityThreshold(rag.getSimilarityThreshold())
                    .filterExpression("category == '" + rag.getCategory() + "'")
                    .build();
            builder.defaultAdvisors(
                    loveSensitiveAdvisor,
                    QuestionAnswerAdvisor.builder(vectorStore)
                            .searchRequest(searchRequest)
                            .build(),
                    reReadingAdvisor,
                    MessageChatMemoryAdvisor.builder(loveMasterChatMemory).build(),
                    loggingAdvisor
            );
        } else {
            builder.defaultAdvisors(
                    loveSensitiveAdvisor,
                    reReadingAdvisor,
                    MessageChatMemoryAdvisor.builder(loveMasterChatMemory).build(),
                    loggingAdvisor
            );
        }
        return builder.build();
    }
}
