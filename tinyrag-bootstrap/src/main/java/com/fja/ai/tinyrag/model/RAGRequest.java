package com.fja.ai.tinyrag.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RAGRequest {

    @NotBlank
    private String question;

    private String kb;

    /**
     * 可选：会话 ID，用于拼接最近历史记忆（最近 5 轮问答）。
     */
    private Long sessionId;
}
