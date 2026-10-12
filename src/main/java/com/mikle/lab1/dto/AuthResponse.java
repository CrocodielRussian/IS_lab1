package com.mikle.lab1.dto;

import java.util.UUID;
import org.springframework.web.util.HtmlUtils;

public record AuthResponse(
        String token,
        UUID id,
        String username
) {
    public AuthResponse {
        username = HtmlUtils.htmlEscape(username, "UTF-8");
    }
}
