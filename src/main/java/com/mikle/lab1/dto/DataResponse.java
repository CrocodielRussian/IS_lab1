package com.mikle.lab1.dto;

import java.util.UUID;
import org.springframework.web.util.HtmlUtils;

public record DataResponse (
        UUID id,
        String name,
        String description
) {
    public DataResponse {
        name = HtmlUtils.htmlEscape(name, "UTF-8");
        description = HtmlUtils.htmlEscape(description, "UTF-8");
    }
}
