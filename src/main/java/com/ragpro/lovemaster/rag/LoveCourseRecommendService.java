package com.ragpro.lovemaster.rag;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.LoveCourseInfo;
import com.ragpro.lovemaster.payment.CourseProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程推荐服务：基于 RAG 检索私有课程知识库，实现变现引导。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoveCourseRecommendService {

    private static final Pattern COURSE_HEADER = Pattern.compile(
            "#\\s*\\[课程\\]\\s*(.+?)(?:\\n|$)", Pattern.MULTILINE);
    private static final Pattern PRICE = Pattern.compile("\\*\\*价格\\*\\*：(.+?)(?:\\||\\n|$)");
    private static final Pattern URL = Pattern.compile("\\*\\*链接\\*\\*：(.+?)(?:\\||\\n|$)");
    private static final Pattern TAGS = Pattern.compile("\\*\\*标签\\*\\*：(.+?)(?:\\n|$)");

    private final VectorStore vectorStore;
    private final LoveMasterProperties properties;
    private final CourseProductService courseProductService;

    public List<LoveCourseInfo> recommend(String question) {
        var rag = properties.getRag();
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.5)
                .filterExpression("category == '" + rag.getCategory() + "' && docType == 'course'")
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);
        return parseCourses(docs, question);
    }

    public List<LoveCourseInfo> recommendFromSources(List<Document> sources, String question) {
        List<Document> courseDocs = sources.stream()
                .filter(doc -> "course".equals(doc.getMetadata().get("docType")))
                .toList();
        if (courseDocs.isEmpty()) {
            return recommend(question);
        }
        return parseCourses(courseDocs, question);
    }

    @Tool(description = "根据用户的恋爱问题，从知识库中检索并推荐合适的课程或服务，用于帮助用户深入学习")
    public List<LoveCourseInfo> recommendCourses(
            @ToolParam(description = "用户的问题或困扰，如：异地恋、吵架、表白") String topic) {
        log.info("Tool: recommendCourses for topic={}", topic);
        return recommend(topic);
    }

    private List<LoveCourseInfo> parseCourses(List<Document> docs, String question) {
        Map<String, LoveCourseInfo> unique = new LinkedHashMap<>();
        for (Document doc : docs) {
            String text = doc.getText();
            Matcher header = COURSE_HEADER.matcher(text);
            if (!header.find()) {
                continue;
            }
            String name = header.group(1).trim();
            String id = String.valueOf(doc.getMetadata().getOrDefault("docId",
                    doc.getMetadata().getOrDefault("courseId", name)));
            if (unique.containsKey(id)) {
                continue;
            }
            unique.put(id, courseProductService.enrich(LoveCourseInfo.builder()
                    .id(id)
                    .name(name)
                    .description(extractDescription(text))
                    .price(extractGroup(text, PRICE))
                    .url(extractGroup(text, URL))
                    .tags(extractGroup(text, TAGS))
                    .reason(buildReason(name, question))
                    .build()));
        }
        return new ArrayList<>(unique.values());
    }

    private String extractGroup(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractDescription(String text) {
        int start = text.indexOf('\n', text.indexOf("[课程]"));
        if (start < 0) {
            return text.length() > 120 ? text.substring(0, 120) + "…" : text;
        }
        String body = text.substring(start).replaceAll("\\*\\*[^*]+\\*\\*：[^\\n|]+", "").trim();
        return body.length() > 150 ? body.substring(0, 150) + "…" : body;
    }

    private String buildReason(String courseName, String question) {
        return "根据你的问题「" + truncate(question, 30) + "」，推荐学习《" + courseName + "》";
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
