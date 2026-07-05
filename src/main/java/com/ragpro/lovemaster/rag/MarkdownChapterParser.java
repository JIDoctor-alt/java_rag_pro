package com.ragpro.lovemaster.rag;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class MarkdownChapterParser {

    private MarkdownChapterParser() {
    }

    @Data
    @Builder
    static class Section {
        private int index;
        private String title;
        private String content;
    }

    @Data
    @Builder(toBuilder = true)
    static class ParsedDocument {
        private String docId;
        private String title;
        private String docType;
        private String relativePath;
        private String fullContent;
        private List<Section> sections;
    }

    static ParsedDocument parse(String docId, String docType, String raw) {
        return parse(docId, docType, null, raw);
    }

    static ParsedDocument parse(String docId, String docType, String relativePath, String raw) {
        String[] lines = raw.split("\n", -1);
        String title = docId;
        List<Section> sections = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        String sectionTitle = null;
        StringBuilder sectionBody = new StringBuilder();
        boolean titleFound = false;

        for (String line : lines) {
            if (line.startsWith("# ") && !titleFound) {
                title = line.substring(2).trim();
                titleFound = true;
                continue;
            }
            if (line.startsWith("## ")) {
                if (sectionTitle != null) {
                    sections.add(buildSection(sections.size(), sectionTitle, sectionBody));
                    sectionBody = new StringBuilder();
                }
                sectionTitle = line.substring(3).trim();
                continue;
            }
            if (sectionTitle != null) {
                sectionBody.append(line).append('\n');
            } else if (titleFound) {
                body.append(line).append('\n');
            }
        }

        if (sectionTitle != null) {
            sections.add(buildSection(sections.size(), sectionTitle, sectionBody));
        }

        if (sections.isEmpty()) {
            String content = body.toString().trim();
            if (!content.isBlank()) {
                sections.add(Section.builder()
                        .index(0)
                        .title(title)
                        .content(content)
                        .build());
            }
        }

        return ParsedDocument.builder()
                .docId(docId)
                .title(title)
                .docType(docType)
                .relativePath(relativePath)
                .fullContent(raw.trim())
                .sections(sections)
                .build();
    }

    static String toMarkdown(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(doc.getTitle()).append("\n\n");
        boolean singleSection = doc.getSections().size() == 1
                && doc.getSections().getFirst().getTitle().equals(doc.getTitle());
        if (singleSection) {
            sb.append(doc.getSections().getFirst().getContent().trim());
            return sb.toString().trim();
        }
        for (Section section : doc.getSections()) {
            sb.append("## ").append(section.getTitle()).append("\n\n");
            if (!section.getContent().isBlank()) {
                sb.append(section.getContent().trim()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    static ParsedDocument updateSection(ParsedDocument doc, int sectionIndex, String editedContent) {
        List<Section> sections = new ArrayList<>(doc.getSections());
        if (sectionIndex < 0 || sectionIndex >= sections.size()) {
            throw new IllegalArgumentException("小节不存在: " + sectionIndex);
        }
        Section current = sections.get(sectionIndex);
        String title = extractSectionTitle(editedContent).orElse(current.getTitle());
        String body = extractSectionBody(editedContent, title);
        sections.set(sectionIndex, Section.builder()
                .index(sectionIndex)
                .title(title)
                .content(body)
                .build());
        String markdown = toMarkdown(doc.toBuilder().sections(sections).build());
        ParsedDocument updated = parse(doc.getDocId(), doc.getDocType(), doc.getRelativePath(), markdown);
        return updated;
    }

    static ParsedDocument updateFullDocument(ParsedDocument doc, String editedContent) {
        String markdown = editedContent.trim();
        if (!markdown.startsWith("# ")) {
            markdown = "# " + doc.getTitle() + "\n\n" + markdown;
        }
        return parse(doc.getDocId(), doc.getDocType(), doc.getRelativePath(), markdown);
    }

    static ParsedDocument deleteSection(ParsedDocument doc, int sectionIndex) {
        List<Section> sections = new ArrayList<>(doc.getSections());
        if (sectionIndex < 0 || sectionIndex >= sections.size()) {
            throw new IllegalArgumentException("小节不存在: " + sectionIndex);
        }
        boolean singleSection = sections.size() == 1
                && sections.getFirst().getTitle().equals(doc.getTitle());
        if (singleSection || sections.size() == 1) {
            throw new IllegalArgumentException("无法删除最后一个章节，请删除整篇文档");
        }
        sections.remove(sectionIndex);
        List<Section> reindexed = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            reindexed.add(Section.builder()
                    .index(i)
                    .title(section.getTitle())
                    .content(section.getContent())
                    .build());
        }
        String markdown = toMarkdown(doc.toBuilder().sections(reindexed).build());
        return parse(doc.getDocId(), doc.getDocType(), doc.getRelativePath(), markdown);
    }

    private static Optional<String> extractSectionTitle(String content) {
        for (String line : content.split("\n", -1)) {
            if (line.startsWith("## ")) {
                return Optional.of(line.substring(3).trim());
            }
        }
        return Optional.empty();
    }

    private static String extractSectionBody(String content, String title) {
        String[] lines = content.split("\n", -1);
        StringBuilder body = new StringBuilder();
        boolean inBody = false;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (line.substring(3).trim().equals(title) || !inBody) {
                    inBody = true;
                    continue;
                }
            }
            if (inBody) {
                body.append(line).append('\n');
            }
        }
        if (body.isEmpty() && !content.contains("## ")) {
            return content.trim();
        }
        return body.toString().trim();
    }

    private static Section buildSection(int index, String title, StringBuilder body) {
        return Section.builder()
                .index(index)
                .title(title.trim())
                .content(body.toString().trim())
                .build();
    }
}
