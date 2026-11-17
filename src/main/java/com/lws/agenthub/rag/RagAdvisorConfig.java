package com.lws.agenthub.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 增强顾问（基于本地向量库）
 *
 * <p>原实现调用阿里云百炼的知识库检索服务（DashScopeDocumentRetriever），
 * 依赖远端索引「知识库助手」，脱离百炼就不存在。
 * 这里换成 Spring AI 自带的 VectorStoreDocumentRetriever：
 * 直接查 knowledgeVectorStore（本地 ONNX 向量化 + 内存向量库），
 * 检索链路完全落在本机，不再有外部知识库依赖。
 */
@Configuration
@Slf4j
public class RagAdvisorConfig {

    @Resource
    private VectorStore knowledgeVectorStore;

    @Bean
    public Advisor cloudRagAdvisor() {
        // topK=4、相似度阈值 0.5：比默认（topK=4、阈值 0）更严，过滤掉明显不相关的片段
        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(knowledgeVectorStore)
                .topK(4)
                .similarityThreshold(0.5)
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .build();
    }
}