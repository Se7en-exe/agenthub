package com.lws.agenthub.config;

import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 运行期可替换的 ChatModel。
 * <p>
 * Spring AI 的 OpenAiChatModel 是不可变对象，密钥在构造时就固化了，
 * 换密钥只能重新 new 一个。这里做一层「会换芯」的外壳：外部拿到的引用始终是同一个，
 * 真正干活的实例放在 volatile 字段里，换 key 时整体替换。
 * <p>
 * 这样 {@code KnowledgeAssistant}、{@code AutoAgent}、{@code QueryRewriter} 这些
 * 启动时就把 ChatModel 注入进去的组件，不用改一行代码也能跟着换模型。
 * <p>
 * 配合 application.yml 里的 {@code spring.ai.model.chat: none}，
 * 关掉自动配置，全应用只有这一个 ChatModel 实现。
 */
@Component
@Slf4j
public class ChatModelProvider implements ChatModel {

    private final ChatSettings settings;

    /** 当前生效的实例；换配置时整体替换，读侧永远读到完整的那个 */
    private volatile OpenAiChatModel delegate;

    /** 每次重建 +1，仅用于日志和排查 */
    private volatile long version = 0;

    public ChatModelProvider(ChatSettings settings) {
        this.settings = settings;
    }

    @PostConstruct
    public void init() {
        this.delegate = build(settings.getApiKey(), settings.getBaseUrl(), settings.getModel(), settings.getTemperature(), null);
        this.version = 1;
        log.info("ChatModel 初始化完成：baseUrl={} model={}", settings.getBaseUrl(), settings.getModel());
    }

    /**
     * 用当前配置重建内部实例。配置已经写进 ChatSettings，这里只负责换芯
     */
    public synchronized void rebuild() {
        this.delegate = build(settings.getApiKey(), settings.getBaseUrl(), settings.getModel(), settings.getTemperature(), null);
        this.version++;
        log.info("ChatModel 已重建，version={}，baseUrl={} model={}",
                version, settings.getBaseUrl(), settings.getModel());
    }

    public long version() {
        return version;
    }

    /**
     * 用一个「还没保存」的参数造临时实例，保存前试连通性用
     */
    public static OpenAiChatModel build(String apiKey, String baseUrl, String modelName,
                                        double temperature, Integer maxTokens) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey == null ? "" : apiKey)
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(temperature);
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options.build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryTemplate.defaultInstance())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate.call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }
}
