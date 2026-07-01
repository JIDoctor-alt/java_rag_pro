package com.ragpro.superagent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelTestResult {

    private String modelId;
    private boolean success;
    private String response;
    private long latencyMs;
    private String error;
}
