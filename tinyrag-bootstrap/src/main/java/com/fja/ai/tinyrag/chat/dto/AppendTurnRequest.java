package com.fja.ai.tinyrag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppendTurnRequest {

    @NotBlank
    private String question;

    private String answer;
    private String rewrittenQuestion;
    private List<String> references;

    /** 检索片段：source、text，与 SSE refs.chunks 结构一致 */
    private List<Map<String, String>> chunks;
}
