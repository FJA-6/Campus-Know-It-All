package com.fja.ai.tinyrag.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
@ConfigurationProperties(prefix = "app.rag")
public class RAGProperties {

    @NotBlank
    private String rerankModel;

    @NotBlank
    private String rerankEndpoint;

    @Min(1)
    @Max(100)
    private Integer retrieveTopK;

    @Min(1)
    @Max(100)
    private Integer rerankTopN;

    @Min(128)
    @Max(1024000)
    private Integer rerankMaxDocumentChars;

    @Min(128)
    @Max(4000)
    private Integer chunkSize;

    @Min(32)
    @Max(1000)
    private Integer minChunkSizeChars;

    @Min(1)
    @Max(100)
    private Integer minChunkLengthToEmbed;

    @Min(1)
    @Max(10000)
    private Integer maxNumChunks;

    /**
     * 当 RAG 没有命中（或命中质量较差）时，是否允许回退到联网检索。
     */
    private Boolean webFallbackEnabled = Boolean.TRUE;

    /**
     * RAG 认为“命中质量足够”的最小向量分数阈值（只在底层向量库返回 score 时生效）。
     * 若无法获取 score，则仅用“是否有命中”作为判定。
     */
    @Min(0)
    @Max(1)
    private Double ragMinTopScore = 0.35;

    /**
     * 当 RAG 无命中时，即便用户未显式要求联网，也允许自动联网补充。
     */
    private Boolean allowWebFallbackOnEmptyRag = Boolean.TRUE;

    /**
     * 联网检索最多返回多少条结果用于拼接上下文。
     */
    @Min(1)
    @Max(10)
    private Integer webSearchMaxResults = 5;

    /**
     * 最终交给生成模型的候选片段总数（RAG + Web 合并后 rerank 输出）。
     */
    @Min(1)
    @Max(20)
    private Integer finalTopChunks = 5;

    /**
     * 当触发联网检索时，RAG 先行保留的最大片段数，避免 RAG 占满所有名额。
     */
    @Min(1)
    @Max(20)
    private Integer ragMaxChunksWhenWebEnabled = 3;

    /**
     * 当触发联网检索时，Web 侧希望保留的最少片段数（若检索端可返回）。
     */
    @Min(1)
    @Max(20)
    private Integer webMinChunksWhenWebEnabled = 2;

    /**
     * 启用“配额优先”策略：当开启联网时，尽量强制补齐 Web 配额（例如 3+2）。
     * 若 Web 端返回为空，则最终仍可能不足配额。
     */
    private Boolean webQuotaPreferEnabled = Boolean.TRUE;

    /**
     * 联网检索优先使用 MCP 工具（例如百度 AIsearch）。
     */
    private Boolean webSearchPreferMcp = Boolean.TRUE;

    /**
     * MCP 联网检索工具名，默认百度 AI Search 的 AIsearch。
     */
    private String webSearchMcpToolName = "AIsearch";

    /**
     * 调用 AIsearch 时可选的大模型名；为空则走基础搜索。
     */
    private String webSearchMcpModel = "";

    /**
     * 当用户问到这些关键词时，更倾向于触发联网检索（简单启发式意图识别）。
     * 用英文逗号分隔，例如：最新,官网,release
     */
    private String webIntentKeywords = "最新,今天,现在,官网,文档,版本,release,news,价格,多少钱，苏州大学，苏大，联网";
}
