package com.ragpro.lovemaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "love-master")
public class LoveMasterProperties {

    private String systemPrompt;
    private int maxMemoryMessages = 30;
    private double temperature = 0.8;
    private RagProperties rag = new RagProperties();

    @Data
    public static class RagProperties {
        /** 是否启用 RAG 检索增强 */
        private boolean enabled = true;
        /** local=PGvector 本地向量库, cloud=百炼云端知识库（预留） */
        private String source = "local";
        private int topK = 5;
        private double similarityThreshold = 0.7;
        private String category = "love-master";
        /** 启动时自动导入 classpath 恋爱知识库 */
        private boolean bootstrapOnStartup = true;
    }
}
