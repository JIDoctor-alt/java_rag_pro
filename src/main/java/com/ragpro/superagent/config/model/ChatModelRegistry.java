package com.ragpro.superagent.config.model;

import com.ragpro.superagent.advisor.LoggingAdvisor;
import com.ragpro.superagent.advisor.SecurityGuardAdvisor;
import com.ragpro.superagent.config.SuperAgentProperties;
import com.ragpro.superagent.model.ModelInfo;
import com.ragpro.superagent.model.ModelTestResult;
import com.ragpro.superagent.tool.FileProcessTool;
import com.ragpro.superagent.tool.PdfGenerateTool;
import com.ragpro.superagent.tool.WebScrapeTool;
import com.ragpro.superagent.tool.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 大模型统一注册中心：管理通义、DeepSeek、豆包、Ollama 的 ChatModel 与 ChatClient。
 */
@Slf4j
@Component
public class ChatModelRegistry {

    private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();
    private final Map<String, ChatClient> agentClients = new LinkedHashMap<>();
    private final Map<String, ChatClient> plainClients = new LinkedHashMap<>();
    private final ModelProperties modelProperties;
    private final SuperAgentProperties superAgentProperties;
    private String defaultModelId;

    public ChatModelRegistry(
            ModelProperties modelProperties,
            SuperAgentProperties superAgentProperties,
            ChatModel primaryChatModel,
            ObjectProvider<DeepSeekChatModel> deepSeekChatModel,
            ObjectProvider<OllamaChatModel> ollamaChatModel,
            @Qualifier("doubaoChatModel") ObjectProvider<ChatModel> doubaoChatModel,
            VectorStore vectorStore,
            ChatMemory chatMemory,
            WebSearchTool webSearchTool,
            WebScrapeTool webScrapeTool,
            PdfGenerateTool pdfGenerateTool,
            FileProcessTool fileProcessTool,
            LoggingAdvisor loggingAdvisor,
            SecurityGuardAdvisor securityGuardAdvisor) {

        this.modelProperties = modelProperties;
        this.superAgentProperties = superAgentProperties;
        this.defaultModelId = superAgentProperties.getDefaultModel();

        ToolCallbackProvider agentTools = MethodToolCallbackProvider.builder()
                .toolObjects(webSearchTool, webScrapeTool, pdfGenerateTool, fileProcessTool)
                .build();

        var searchRequest = SearchRequest.builder()
                .topK(superAgentProperties.getRag().getTopK())
                .similarityThreshold(superAgentProperties.getRag().getSimilarityThreshold())
                .build();

        registerProvider("dashscope", primaryChatModel, modelProperties.getProvider("dashscope"),
                vectorStore, chatMemory, agentTools, loggingAdvisor, securityGuardAdvisor, searchRequest);

        if (isEnabled("deepseek")) {
            deepSeekChatModel.ifAvailable(model ->
                    registerProvider("deepseek", model, modelProperties.getProvider("deepseek"),
                            vectorStore, chatMemory, agentTools, loggingAdvisor, securityGuardAdvisor, searchRequest));
        }

        if (isEnabled("doubao")) {
            doubaoChatModel.ifAvailable(model ->
                    registerProvider("doubao", model, modelProperties.getProvider("doubao"),
                            vectorStore, chatMemory, agentTools, loggingAdvisor, securityGuardAdvisor, searchRequest));
        }

        if (isEnabled("ollama")) {
            ollamaChatModel.ifAvailable(model ->
                    registerProvider("ollama", model, modelProperties.getProvider("ollama"),
                            vectorStore, chatMemory, agentTools, loggingAdvisor, securityGuardAdvisor, searchRequest));
        }

        if (chatModels.isEmpty()) {
            throw new IllegalStateException("未检测到可用的大模型，请至少配置一个模型的 API Key");
        }

        if (!chatModels.containsKey(defaultModelId)) {
            defaultModelId = chatModels.keySet().iterator().next();
            log.warn("默认模型 {} 不可用，已切换为 {}", superAgentProperties.getDefaultModel(), defaultModelId);
        }

        log.info("ChatModelRegistry initialized with models: {}", chatModels.keySet());
    }

    private void registerProvider(
            String id,
            ChatModel chatModel,
            ModelProperties.ProviderConfig config,
            VectorStore vectorStore,
            ChatMemory chatMemory,
            ToolCallbackProvider agentTools,
            LoggingAdvisor loggingAdvisor,
            SecurityGuardAdvisor securityGuardAdvisor,
            SearchRequest searchRequest) {

        chatModels.put(id, chatModel);

        ChatClient agentClient = ChatClient.builder(chatModel)
                .defaultSystem(superAgentProperties.getSystemPrompt())
                .defaultAdvisors(
                        securityGuardAdvisor,
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(searchRequest)
                                .build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        loggingAdvisor
                )
                .defaultToolCallbacks(agentTools)
                .build();

        ChatClient plainClient = ChatClient.builder(chatModel)
                .defaultSystem(superAgentProperties.getSystemPrompt())
                .build();

        agentClients.put(id, agentClient);
        plainClients.put(id, plainClient);
        log.info("Registered model [{}] -> {}", id, config.getModel());
    }

    public ChatClient getAgentClient(String modelId) {
        String id = resolveModelId(modelId);
        return Optional.ofNullable(agentClients.get(id))
                .orElseThrow(() -> new IllegalArgumentException("模型不可用: " + modelId));
    }

    public ChatClient getPlainClient(String modelId) {
        String id = resolveModelId(modelId);
        return Optional.ofNullable(plainClients.get(id))
                .orElseThrow(() -> new IllegalArgumentException("模型不可用: " + modelId));
    }

    public ChatModel getChatModel(String modelId) {
        String id = resolveModelId(modelId);
        return Optional.ofNullable(chatModels.get(id))
                .orElseThrow(() -> new IllegalArgumentException("模型不可用: " + modelId));
    }

    public ChatClient getDefaultAgentClient() {
        return getAgentClient(defaultModelId);
    }

    public String getDefaultModelId() {
        return defaultModelId;
    }

    public List<ModelInfo> listModels() {
        List<ModelInfo> result = new ArrayList<>();
        modelProperties.getProviders().forEach((id, config) -> {
            boolean registered = chatModels.containsKey(id);
            result.add(ModelInfo.builder()
                    .id(id)
                    .name(config.getDisplayName())
                    .provider(id)
                    .modelName(config.getModel())
                    .enabled(isEnabled(id))
                    .available(registered)
                    .description(buildDescription(id, config, registered))
                    .build());
        });
        return result;
    }

    public ModelTestResult testModel(String modelId) {
        long start = System.currentTimeMillis();
        try {
            ChatModel model = getChatModel(modelId);
            ChatResponse response = model.call(new Prompt("你好，请用一句话介绍你自己"));
            String content = response.getResult().getOutput().getText();
            return ModelTestResult.builder()
                    .modelId(resolveModelId(modelId))
                    .success(true)
                    .response(content)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("Model test failed for {}", modelId, e);
            return ModelTestResult.builder()
                    .modelId(modelId)
                    .success(false)
                    .latencyMs(System.currentTimeMillis() - start)
                    .error(e.getMessage())
                    .build();
        }
    }

    private String resolveModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return defaultModelId;
        }
        String id = modelId.toLowerCase();
        if (!chatModels.containsKey(id)) {
            throw new IllegalArgumentException("模型不可用: " + modelId + "，可用模型: " + chatModels.keySet());
        }
        return id;
    }

    private boolean isEnabled(String id) {
        return modelProperties.getProvider(id).isEnabled();
    }

    private String buildDescription(String id, ModelProperties.ProviderConfig config, boolean registered) {
        if (!config.isEnabled()) {
            return "已禁用";
        }
        if (!registered) {
            return switch (id) {
                case "dashscope" -> "请配置 DASHSCOPE_API_KEY";
                case "deepseek" -> "请配置 DEEPSEEK_API_KEY";
                case "doubao" -> "请配置 DOUBAO_API_KEY 并设置 enabled=true";
                case "ollama" -> "请启动 Ollama 服务";
                default -> "未接入";
            };
        }
        return config.getDisplayName() + " (" + config.getModel() + ")";
    }
}
