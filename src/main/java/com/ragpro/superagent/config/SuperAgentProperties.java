package com.ragpro.superagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "super-agent")
public class SuperAgentProperties {

    private String systemPrompt;
    private String defaultModel = "dashscope";
    private RagProperties rag = new RagProperties();

    @Data
    public static class RagProperties {
        private int topK = 5;
        private double similarityThreshold = 0.7;
    }
}
