package com.ragpro.lovemaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "love-master")
public class LoveMasterProperties {

    private String systemPrompt;
    private int maxMemoryMessages = 30;
    private double temperature = 0.8;
}
