package com.ragpro.lovemaster.service;

import com.ragpro.lovemaster.model.LoveReport;
import com.ragpro.lovemaster.model.LoveReportRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Map;

@Slf4j
@Service
public class LoveMasterService {

    private static final String CONVERSATION_PREFIX = "love-";

    private final ChatClient loveMasterChatClient;
    private final Resource chatPromptTemplate;
    private final Resource reportPromptTemplate;
    private final Resource multimodalPromptTemplate;

    public LoveMasterService(
            @Qualifier("loveMasterChatClient") ChatClient loveMasterChatClient,
            @Value("classpath:prompts/lovemaster/chat.st") Resource chatPromptTemplate,
            @Value("classpath:prompts/lovemaster/report.st") Resource reportPromptTemplate,
            @Value("classpath:prompts/lovemaster/multimodal.st") Resource multimodalPromptTemplate) {
        this.loveMasterChatClient = loveMasterChatClient;
        this.chatPromptTemplate = chatPromptTemplate;
        this.reportPromptTemplate = reportPromptTemplate;
        this.multimodalPromptTemplate = multimodalPromptTemplate;
    }

    public String chat(String message, String conversationId) {
        return chat(message, conversationId, null);
    }

    public String chat(String message, String conversationId, String imageUrl) {
        String memoryId = toMemoryId(conversationId);
        if (StringUtils.hasText(imageUrl)) {
            return chatWithImage(message, imageUrl, memoryId);
        }
        String prompt = renderTemplate(chatPromptTemplate, Map.of("message", message));
        return loveMasterChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId))
                .call()
                .content();
    }

    public Flux<String> chatStream(String message, String conversationId) {
        String memoryId = toMemoryId(conversationId);
        String prompt = renderTemplate(chatPromptTemplate, Map.of("message", message));
        return loveMasterChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId))
                .stream()
                .content();
    }

    public LoveReport generateReport(LoveReportRequest request) {
        String memoryId = toMemoryId(request.getConversationId());
        String prompt = renderTemplate(reportPromptTemplate, Map.of(
                "name", request.getName(),
                "gender", request.getGender(),
                "situation", request.getSituation(),
                "partnerInfo", request.getPartnerInfo()
        ));
        log.info("Generating love report for user: {}", request.getName());
        return loveMasterChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId))
                .call()
                .entity(LoveReport.class);
    }

    private String chatWithImage(String message, String imageUrl, String memoryId) {
        String prompt = renderTemplate(multimodalPromptTemplate, Map.of("message", message));
        try {
            UserMessage userMessage = UserMessage.builder()
                    .text(prompt)
                    .media(new Media(MimeTypeUtils.IMAGE_JPEG, new URI(imageUrl)))
                    .build();
            return loveMasterChatClient.prompt()
                    .messages(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Multimodal failed, fallback to text: {}", e.getMessage());
            return chat(message, memoryId.replace(CONVERSATION_PREFIX, ""), null);
        }
    }

    private String renderTemplate(Resource resource, Map<String, Object> variables) {
        return PromptTemplate.builder()
                .resource(resource)
                .build()
                .render(variables);
    }

    private String toMemoryId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return CONVERSATION_PREFIX + "default";
        }
        return conversationId.startsWith(CONVERSATION_PREFIX)
                ? conversationId
                : CONVERSATION_PREFIX + conversationId;
    }
}
