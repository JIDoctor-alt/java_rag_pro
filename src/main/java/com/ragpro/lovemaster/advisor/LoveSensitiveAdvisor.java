package com.ragpro.lovemaster.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 恋爱场景安全守卫：拦截极端/危险内容，引导用户寻求专业帮助。
 */
@Component
public class LoveSensitiveAdvisor implements CallAdvisor, StreamAdvisor {

    private static final List<Pattern> CRISIS_PATTERNS = List.of(
            Pattern.compile("自杀|自残|不想活|结束生命"),
            Pattern.compile("报复|伤害对方|同归于尽")
    );

    private static final String CRISIS_RESPONSE = """
            我感受到你现在可能非常痛苦。你的感受是重要的，但这类问题已经超出了恋爱咨询的范围。
            请立即联系身边信任的人，或拨打心理援助热线 400-161-9995 寻求专业帮助。
            你并不孤单，专业人士可以帮助你度过这个难关。
            """;

    @Override
    public String getName() {
        return "LoveSensitiveAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String text = request.prompt().getUserMessage().getText();
        if (containsCrisis(text)) {
            throw new LoveSensitiveException(CRISIS_RESPONSE);
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String text = request.prompt().getUserMessage().getText();
        if (containsCrisis(text)) {
            return Flux.error(new LoveSensitiveException(CRISIS_RESPONSE));
        }
        return chain.nextStream(request);
    }

    private boolean containsCrisis(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CRISIS_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    public static class LoveSensitiveException extends RuntimeException {
        public LoveSensitiveException(String message) {
            super(message);
        }
    }
}
