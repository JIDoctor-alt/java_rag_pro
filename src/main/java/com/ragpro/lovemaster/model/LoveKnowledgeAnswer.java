package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoveKnowledgeAnswer {

    private String question;
    private String answer;
    private List<RetrievedChunk> sources;
    private List<LoveCourseInfo> recommendedCourses;
    private String ragSource;

    @Data
    @Builder
    public static class RetrievedChunk {
        private String content;
        private String title;
        private double score;
    }
}
