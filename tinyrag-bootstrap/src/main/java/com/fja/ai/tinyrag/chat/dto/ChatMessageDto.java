package com.fja.ai.tinyrag.chat.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatMessageDto {

    private final Long id;
    private final String role;
    private final String content;
    private final String metadataJson;
    private final Instant createdAt;
}
