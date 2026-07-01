package com.ragpro.superagent.tool;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class PdfGenerateTool {

    private final Path outputDir;

    public PdfGenerateTool(@Value("${user.home}/ai-super-agent/output") String outputDir) throws IOException {
        this.outputDir = Path.of(outputDir);
        Files.createDirectories(this.outputDir);
    }

    @Tool(description = "将文本内容生成为 PDF 文件，返回文件路径")
    public String generatePdf(
            @ToolParam(description = "PDF 文件标题") String title,
            @ToolParam(description = "PDF 正文内容") String content) {
        String fileName = sanitize(title) + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
        Path filePath = outputDir.resolve(fileName);
        try {
            try (PdfWriter writer = new PdfWriter(filePath.toFile());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                document.add(new Paragraph(title).setFontSize(18));
                document.add(new Paragraph(content));
            }
            log.info("PDF generated: {}", filePath);
            return filePath.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("PDF generation failed", e);
            return "PDF 生成失败: " + e.getMessage();
        }
    }

    private String sanitize(String title) {
        return title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
    }
}
