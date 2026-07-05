package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class LoveKnowledgeChapterNode {

    private String id;
    private String docId;
    private Integer sectionIndex;
    private String title;
    private String docType;
    private String parentId;
    private int sortOrder;
    private boolean selectable;

    @Builder.Default
    private List<LoveKnowledgeChapterNode> children = new ArrayList<>();
}
