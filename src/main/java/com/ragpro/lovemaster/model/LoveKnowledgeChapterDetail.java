package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoveKnowledgeChapterDetail {

    private String id;
    private String docId;
    private Integer sectionIndex;
    private String title;
    private String content;
    private String docType;
    private String chapterPath;
    private List<Breadcrumb> breadcrumb;

    @Data
    @Builder
    public static class Breadcrumb {
        private String id;
        private String title;
    }
}
