package com.ragpro.superagent.config.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "super-agent.models")
public class ModelProperties {

    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    @Data
    public static class ProviderConfig {
        private boolean enabled = true;
        private String displayName;
        private String model;
        private String apiKey;
        private String baseUrl;
        private String completionsPath;
        private Double temperature = 0.7;
    }

    public ProviderConfig getProvider(String id) {
        return providers.getOrDefault(id, new ProviderConfig());
    }
}
