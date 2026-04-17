package com.fja.ai.tinyrag.chat.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessionSummaryDto {

    private final Long id;
    private final String title;
    private final String kb;
    private final Instant updatedAt;
}
