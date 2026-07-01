package com.ragpro.superagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class FileProcessTool {

    @Tool(description = "读取本地文本文件内容")
    public String readTextFile(
            @ToolParam(description = "文件绝对路径") String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            if (Files.size(path) > 512_000) {
                return "文件过大（超过 512KB），请使用其他方式处理";
            }
            String content = Files.readString(path);
            log.info("Read file: {} ({} chars)", filePath, content.length());
            return content;
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            return "文件读取失败: " + e.getMessage();
        }
    }

    @Tool(description = "将文本内容写入本地文件")
    public String writeTextFile(
            @ToolParam(description = "文件绝对路径") String filePath,
            @ToolParam(description = "要写入的文本内容") String content) {
        try {
            Path path = Path.of(filePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            log.info("Wrote file: {}", filePath);
            return "文件已写入: " + filePath;
        } catch (IOException e) {
            log.error("Failed to write file: {}", filePath, e);
            return "文件写入失败: " + e.getMessage();
        }
    }
}
