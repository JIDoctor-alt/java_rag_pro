package com.ragpro.lovemaster.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * ReReading Advisor：在调用模型前扩展用户问题，引导模型关注情感细节与潜台词。
 */
@Component
public class ReReadingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String RE_READING_HINT = """

            【ReReading 提示】请仔细重读用户的表述，关注以下维度：
            1. 用户真正想表达的情感需求是什么？
            2. 有没有未直接说出口的顾虑或期待？
            3. 当前处于关系的哪个阶段？
            请基于以上理解，给出温暖、具体、可执行的建议。
            """;

    @Override
    public String getName() {
        return "ReReadingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(augmentRequest(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(augmentRequest(request));
    }

    private ChatClientRequest augmentRequest(ChatClientRequest request) {
        UserMessage userMessage = request.prompt().getUserMessage();
        String original = userMessage.getText();
        if (original == null || original.isBlank()) {
            return request;
        }
        String augmented = original + RE_READING_HINT;
        Prompt newPrompt = request.prompt().augmentUserMessage(msg -> UserMessage.builder()
                .text(augmented)
                .media(userMessage.getMedia())
                .build());
        return request.mutate().prompt(newPrompt).build();
    }
}
