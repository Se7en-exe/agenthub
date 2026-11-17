package com.lws.agenthub.app;

import com.lws.agenthub.advisor.MyLoggerAdvisor;
import com.lws.agenthub.advisor.ReReadingAdvisor;
import com.lws.agenthub.chatmemory.FileBasedChatMemory;
import com.lws.agenthub.config.ModelErrors;
import com.lws.agenthub.rag.RagAdvisorFactory;
import com.lws.agenthub.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class KnowledgeAssistant {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是 AgentHub 的知识库问答助手，答案只能来自检索到的文档片段。" +
            "判断标准是「检索到的片段里有没有答案」，而不是「这篇文档是不是平台官方文档」。" +
            "只要片段里写明了用户问的事，就依据它回答，并在答案里说明依据来自哪一篇文档的哪一节；" +
            "片段之间如果有冲突，以更具体的那一条为准。" +
            "只有检索片段里确实找不到答案时，才说这部分不在知识范围内，不要凭常识补全。" +
            "用户描述不清时先追问：问的是哪份文档、哪个功能、报什么错。" +
            "回答用纯文本口语表达，不要使用 Markdown 语法（# 标题、** 加粗、| 表格都不行），" +
            "需要列举时用「1. 2. 3.」这样的编号换行列出。";

    /**
     * 初始化 ChatClient
     *
     * @param chatModel
     */
    public KnowledgeAssistant(ChatModel chatModel) {
        // 初始化基于内存的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        // 这里的 chatModel 是 ChatModelProvider（动态代理），
        // 页面里换了 API Key 之后它会指向新实例，不用重建这个 ChatClient
        chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
//                      // 自定义推理增强 Advisor，可按需开启
//                     ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        String conversationId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        String conversationId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    record AnswerDigest(String title, List<String> suggestions) {

    }

    /**
     * 结构化输出：把一次答复整理成「结论 + 要点清单」
     *
     * @param message
     * @param chatId
     * @return
     */
    public AnswerDigest doChatWithReport(String message, String chatId) {
        AnswerDigest answerDigest = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都整理成一份答复摘要，标题为{用户名}的问题小结，内容为要点列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(AnswerDigest.class);
        log.info("answerDigest: {}", answerDigest);
        return answerDigest;
    }

    // 知识库检索增强问答

    @Resource
    private VectorStore knowledgeVectorStore;

    @Resource
    private Advisor cloudRagAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        String conversationId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用改写后的查询
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(knowledgeVectorStore))
                // 应用 RAG 检索增强服务（基于云知识库服务）
//                .advisors(cloudRagAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        RagAdvisorFactory.createKnowledgeAssistantRagCustomAdvisor(
//                                knowledgeVectorStore, "产品"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 和 RAG 知识库对话（流式）。检索逻辑与 {@link #doChatWithRag} 完全一致，
     * 只是把最后一步的 call() 换成 stream()，让前端可以看到逐字输出。
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithRagByStream(String message, String chatId) {
        // chatId 允许为空（直接用 curl 调试时就没人传），但 ConversationId 为 null 会让
        // DefaultChatClient.param() 抛 "value cannot be null"，这里兜一个默认会话
        final String conversationId = (chatId == null || chatId.isBlank()) ? "default" : chatId;
        final String rewrittenMessage;
        try {
            rewrittenMessage = queryRewriter.doQueryRewrite(message);
        } catch (Exception e) {
            // 查询重写这一步自己就要调一次模型，key 不可用时会在建流之前就抛出来。
            // 直接把异常抛给 Spring 会变成一个空的 500 JSON，前端只会显示连接异常，
            // 所以这里先降级成用原问题检索，让下面的流去处理真正的报错。
            log.warn("查询重写失败，改用原问题检索：{}", ModelErrors.describe(e));
            return answerWithRagStream(message, conversationId);
        }
        return answerWithRagStream(rewrittenMessage, conversationId);
    }

    /**
     * 真正发一次带检索的流式请求；模型侧报错也转成一句话回给前端，而不是断流
     */
    private Flux<String> answerWithRagStream(String question, String conversationId) {
        return chatClient
                .prompt()
                .user(question)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(knowledgeVectorStore))
                .stream()
                .content()
                .onErrorResume(e -> {
                    String reason = ModelErrors.describe(e);
                    log.error("知识库问答失败：{}", reason);
                    return Flux.just("回答失败了：" + reason);
                })
                // 末尾补一个结束标记：前端收到后主动关闭连接，
                // 不用靠 onerror 去猜这条流是正常结束还是断了
                .concatWith(Flux.just("[DONE]"));
    }

    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * 带工具调用的问答（联网搜索 / 抓页面 / 读写文件 / 终端 / PDF）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // AI 调用 MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * 通过 MCP 协议调用外部工具服务
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
