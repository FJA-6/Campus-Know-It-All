package com.fja.ai.tinyrag.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatSessionCreatedDto {

    private final Long id;
    private final String title;
    private final String kb;
}
