package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoveCourseInfo {

    private String id;
    private String name;
    private String description;
    private String price;
    private String url;
    private String reason;
    private String tags;
}
