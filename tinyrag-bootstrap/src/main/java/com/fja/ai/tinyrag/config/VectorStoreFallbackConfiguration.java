package com.fja.ai.tinyrag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 当未启用 Milvus（或 Milvus 未提供 VectorStore Bean）时，默认使用内存向量库，
 * 以保证项目在不部署 Milvus 的情况下也能启动并运行其它功能。
 */
@Configuration
public class VectorStoreFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}

