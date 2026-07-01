package com.ragpro.superagent.agent;

import com.ragpro.superagent.config.model.ChatModelRegistry;
import com.ragpro.superagent.model.ModelInfo;
import com.ragpro.superagent.model.ModelTestResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class SuperAgentService {

    private final ChatModelRegistry modelRegistry;

    public SuperAgentService(ChatModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public String chat(String message, String conversationId) {
        return chat(message, conversationId, null);
    }

    public String chat(String message, String conversationId, String model) {
        return resolveClient(model).prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public Flux<String> chatStream(String message, String conversationId) {
        return chatStream(message, conversationId, null);
    }

    public Flux<String> chatStream(String message, String conversationId, String model) {
        return resolveClient(model).prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    public List<ModelInfo> listModels() {
        return modelRegistry.listModels();
    }

    public ModelTestResult testModel(String modelId) {
        return modelRegistry.testModel(modelId);
    }

    public String defaultModel() {
        return modelRegistry.getDefaultModelId();
    }

    private ChatClient resolveClient(String model) {
        if (model == null || model.isBlank()) {
            return modelRegistry.getDefaultAgentClient();
        }
        return modelRegistry.getAgentClient(model);
    }
}
