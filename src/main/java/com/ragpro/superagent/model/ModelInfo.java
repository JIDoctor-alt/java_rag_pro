package com.ragpro.superagent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelInfo {

    private String id;
    private String name;
    private String provider;
    private String modelName;
    private boolean enabled;
    private boolean available;
    private String description;
}
