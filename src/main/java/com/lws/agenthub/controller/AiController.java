package com.lws.agenthub.controller;

import com.lws.agenthub.agent.AutoAgent;
import com.lws.agenthub.app.KnowledgeAssistant;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private KnowledgeAssistant knowledgeAssistant;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel chatModel;

    /**
     * 同步调用 知识库助手应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/assistant/chat/sync")
    public String doChatWithKnowledgeAssistantSync(String message, String chatId) {
        return knowledgeAssistant.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 知识库助手应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/assistant/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithKnowledgeAssistantSSE(String message, String chatId) {
        return knowledgeAssistant.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 知识库助手应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/assistant/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithKnowledgeAssistantServerSentEvent(String message, String chatId) {
        return knowledgeAssistant.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 知识库助手应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/assistant/chat/sse_emitter")
    public SseEmitter doChatWithKnowledgeAssistantServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        knowledgeAssistant.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 知识库问答（RAG）：本地 ONNX 向量化 + 内存向量库检索增强
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/assistant/chat/rag")
    public String doChatWithKnowledgeAssistantRag(String message, String chatId) {
        return knowledgeAssistant.doChatWithRag(message, chatId);
    }

    /**
     * 知识库问答（RAG）流式版：检索增强 + SSE 逐字推送
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/assistant/chat/rag/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithKnowledgeAssistantRagSse(String message, String chatId) {
        return knowledgeAssistant.doChatWithRagByStream(message, chatId);
    }

    /**
     * 流式调用自主智能体（SSE 逐步推送 ReAct 每一步）
     *
     * @param message
     * @return
     */
    @GetMapping("/agent/chat")
    public SseEmitter doChatWithAgent(String message) {
        AutoAgent autoAgent = new AutoAgent(allTools, chatModel);
        return autoAgent.runStream(message);
    }
}
