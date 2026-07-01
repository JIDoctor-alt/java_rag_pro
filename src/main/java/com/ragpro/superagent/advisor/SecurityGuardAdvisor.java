package com.ragpro.superagent.advisor;

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
 * 前置安全守卫：拦截敏感词与危险指令。
 */
@Component
public class SecurityGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+previous\\s+instructions"),
            Pattern.compile("(?i)system\\s+prompt\\s+leak"),
            Pattern.compile("删除.*数据库", Pattern.UNICODE_CASE)
    );

    @Override
    public String getName() {
        return "SecurityGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();
        if (isBlocked(userText)) {
            throw new SecurityException("请求包含不允许的内容，已被安全守卫拦截");
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();
        if (isBlocked(userText)) {
            return Flux.error(new SecurityException("请求包含不允许的内容，已被安全守卫拦截"));
        }
        return chain.nextStream(request);
    }

    private boolean isBlocked(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return BLOCKED_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
