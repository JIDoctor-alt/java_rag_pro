package com.ragpro.superagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebScrapeTool {

    @Tool(description = "抓取指定 URL 的网页正文内容，用于获取网页信息")
    public String scrapeWebPage(
            @ToolParam(description = "要抓取的网页 URL") String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 AI-Super-Agent/1.0")
                    .timeout(10_000)
                    .get();
            String text = doc.body().text();
            log.info("Scraped {} chars from {}", text.length(), url);
            return text.length() > 8000 ? text.substring(0, 8000) + "..." : text;
        } catch (Exception e) {
            log.error("Failed to scrape {}", url, e);
            return "网页抓取失败: " + e.getMessage();
        }
    }
}
