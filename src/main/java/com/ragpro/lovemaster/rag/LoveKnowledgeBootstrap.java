package com.ragpro.lovemaster.rag;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时自动导入 classpath 恋爱知识库（本地 RAG 方式一）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoveKnowledgeBootstrap implements ApplicationRunner {

    private final LoveKnowledgeService loveKnowledgeService;
    private final LoveMasterProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getRag().isEnabled() || !properties.getRag().isBootstrapOnStartup()) {
            return;
        }
        try {
            int chunks = loveKnowledgeService.bootstrapFromClasspath();
            log.info("Love knowledge bootstrap completed, {} chunks ingested", chunks);
        } catch (Exception e) {
            log.warn("Love knowledge bootstrap skipped: {}", e.getMessage());
        }
    }
}
