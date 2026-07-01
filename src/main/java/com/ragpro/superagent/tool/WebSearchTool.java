package com.ragpro.superagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class WebSearchTool {

    private final RestClient restClient;
    private final String searchApiKey;

    public WebSearchTool(
            RestClient.Builder restClientBuilder,
            @Value("${SEARCH_API_KEY:}") String searchApiKey) {
        this.restClient = restClientBuilder.build();
        this.searchApiKey = searchApiKey;
    }

    @Tool(description = "联网搜索，获取最新信息。当用户询问实时新闻、天气或最新事件时使用")
    public String searchWeb(
            @ToolParam(description = "搜索关键词") String query) {
        if (searchApiKey == null || searchApiKey.isBlank()) {
            return "联网搜索未配置（请设置 SEARCH_API_KEY 环境变量），当前无法搜索: " + query;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("https://www.searchapi.io/api/v1/search?engine=google&q={q}&api_key={key}",
                            query, searchApiKey)
                    .retrieve()
                    .body(Map.class);
            log.info("Web search completed for: {}", query);
            return String.valueOf(response);
        } catch (Exception e) {
            log.error("Web search failed for: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }
}
