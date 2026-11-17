package com.lws.agenthub.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 知识库助手向量数据库配置（基于内存的向量数据库 Bean）
 *
 * <p>原实现把「加载文档 + 逐条调用大模型补关键词 + 写入向量库」全部写在 @Bean 方法里，
 * 容器启动阶段就会同步执行。只要 DashScope 的 api-key 没配（或额度用尽），
 * KeywordMetadataEnricher 会抛 HTTP 401，整个 Spring 容器 APPLICATION FAILED TO START，
 * 连不依赖向量库的接口都访问不到。
 *
 * <p>这里改成懒加载：@Bean 只返回一个空的委托壳，真正的构建推迟到第一次读写向量库时。
 * 这样启动阶段不碰远端服务，缺 key 也能把应用起来；等真正用到知识库问答时再初始化，
 * 那时才需要 key。关键词增强失败时降级为原始文档，不阻断整个流程。
 */
@Configuration
@Slf4j
public class KnowledgeVectorStoreConfig {

    @Resource
    private KnowledgeDocumentLoader knowledgeDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Resource
    private KnowledgeRegistry knowledgeRegistry;

    @Bean
    VectorStore knowledgeVectorStore(EmbeddingModel embeddingModel) {
        return new LazySimpleVectorStore(embeddingModel);
    }

    /**
     * 懒加载的向量库包装：容器启动时不构建，第一次读写时才初始化底层 SimpleVectorStore。
     */
    private class LazySimpleVectorStore implements VectorStore {

        private final EmbeddingModel embeddingModel;

        /** 真正的向量库，未初始化时为 null；volatile 保证双重检查锁的可见性 */
        private volatile VectorStore delegate;

        LazySimpleVectorStore(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        /**
         * 双重检查锁：只有第一次访问才真正构建，之后直接返回已建好的实例。
         */
        private VectorStore delegate() {
            VectorStore local = delegate;
            if (local == null) {
                synchronized (this) {
                    local = delegate;
                    if (local == null) {
                        local = build();
                        delegate = local;
                    }
                }
            }
            return local;
        }

        private VectorStore build() {
            log.info("首次使用知识库，开始构建内存向量库");
            SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
            // 加载文档
            List<Document> documents = knowledgeDocumentLoader.loadMarkdowns();
            // 自动补充关键词元信息；失败时降级为原始文档，不阻断启动
            try {
                documents = myKeywordEnricher.enrichDocuments(documents);
            } catch (Exception e) {
                log.warn("关键词元信息增强失败，降级使用原始文档：{}", e.getMessage());
            }
            store.add(documents);
            registerBuiltin(documents);
            log.info("内存向量库构建完成，文档数：{}", documents.size());
            return store;
        }

        /**
         * 把随包分发的 markdown 按文件名归组登记，让它们和上传的文件一样能在列表里看到、能被删除。
         * 分组依据是元数据里的 filename，因为一个文件往往被切成很多个切片。
         */
        private void registerBuiltin(List<Document> documents) {
            java.util.Map<String, List<String>> grouped = new java.util.LinkedHashMap<>();
            for (Document d : documents) {
                Object name = d.getMetadata().get("filename");
                grouped.computeIfAbsent(String.valueOf(name), k -> new java.util.ArrayList<>()).add(d.getId());
            }
            grouped.forEach((name, ids) -> knowledgeRegistry.register(name, ids, "builtin"));
            knowledgeRegistry.markReady();
        }

        @Override
        public void add(List<Document> documents) {
            delegate().add(documents);
        }

        @Override
        public void delete(List<String> idList) {
            delegate().delete(idList);
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            delegate().delete(filterExpression);
        }

        @Override
        public void delete(String filterExpression) {
            delegate().delete(filterExpression);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return delegate().similaritySearch(request);
        }
    }
}
