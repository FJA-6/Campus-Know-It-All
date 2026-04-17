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
}
