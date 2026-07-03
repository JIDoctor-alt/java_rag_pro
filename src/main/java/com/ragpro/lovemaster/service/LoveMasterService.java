package com.ragpro.lovemaster.service;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.LoveCourseInfo;
import com.ragpro.lovemaster.model.LoveKnowledgeAnswer;
import com.ragpro.lovemaster.model.LoveKnowledgeRequest;
import com.ragpro.lovemaster.model.LoveReport;
import com.ragpro.lovemaster.model.LoveReportRequest;
import com.ragpro.lovemaster.rag.LoveCourseRecommendService;
import com.ragpro.lovemaster.rag.LoveKnowledgeService;
import org.springframework.ai.document.Document;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LoveMasterService {

    private static final String CONVERSATION_PREFIX = "love-";

    private final ChatClient loveMasterChatClient;
    private final Resource chatPromptTemplate;
    private final Resource reportPromptTemplate;
    private final Resource multimodalPromptTemplate;
    private final Resource ragQaPromptTemplate;
    private final LoveKnowledgeService loveKnowledgeService;
    private final LoveCourseRecommendService courseRecommendService;
    private final LoveMasterProperties properties;

    public LoveMasterService(
            @Qualifier("loveMasterChatClient") ChatClient loveMasterChatClient,
            @Value("classpath:prompts/lovemaster/chat.st") Resource chatPromptTemplate,
            @Value("classpath:prompts/lovemaster/report.st") Resource reportPromptTemplate,
            @Value("classpath:prompts/lovemaster/multimodal.st") Resource multimodalPromptTemplate,
            @Value("classpath:prompts/lovemaster/rag-qa.st") Resource ragQaPromptTemplate,
            LoveKnowledgeService loveKnowledgeService,
            LoveCourseRecommendService courseRecommendService,
            LoveMasterProperties properties) {
        this.loveMasterChatClient = loveMasterChatClient;
        this.chatPromptTemplate = chatPromptTemplate;
        this.reportPromptTemplate = reportPromptTemplate;
        this.multimodalPromptTemplate = multimodalPromptTemplate;
        this.ragQaPromptTemplate = ragQaPromptTemplate;
        this.loveKnowledgeService = loveKnowledgeService;
        this.courseRecommendService = courseRecommendService;
        this.properties = properties;
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

    /**
     * RAG 知识问答：Retrieve → Augment → Generate
     */
    public LoveKnowledgeAnswer askKnowledge(LoveKnowledgeRequest request) {
        String memoryId = toMemoryId(request.getConversationId());
        List<Document> sources = loveKnowledgeService.retrieve(request.getQuestion());
        String prompt = renderTemplate(ragQaPromptTemplate, Map.of("question", request.getQuestion()));

        String answer = loveMasterChatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId))
                .call()
                .content();

        List<LoveKnowledgeAnswer.RetrievedChunk> chunks = sources.stream()
                .map(doc -> LoveKnowledgeAnswer.RetrievedChunk.builder()
                        .content(doc.getText())
                        .title(String.valueOf(doc.getMetadata().getOrDefault("title", "知识片段")))
                        .score(doc.getScore() != null ? doc.getScore() : 0.0)
                        .build())
                .toList();

        List<LoveCourseInfo> courses = courseRecommendService.recommendFromSources(sources, request.getQuestion());

        return LoveKnowledgeAnswer.builder()
                .question(request.getQuestion())
                .answer(answer)
                .sources(chunks)
                .recommendedCourses(courses)
                .ragSource(properties.getRag().getSource())
                .build();
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
