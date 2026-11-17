package com.lws.agenthub.controller;

import com.lws.agenthub.config.ChatModelProvider;
import com.lws.agenthub.config.ChatSettings;
import com.lws.agenthub.config.ModelErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大模型连接设置：查看、试连、保存、恢复默认。
 * <p>
 * 密钥只以打码形式回显，接口永远不把明文 key 吐给前端；
 * 保存前会先用新参数真发一次请求，连不通就不落盘，避免把服务改坏。
 */
@RestController
@RequestMapping("/ai/settings")
@Slf4j
public class SettingsController {

    private final ChatSettings settings;

    private final ChatModelProvider provider;

    public SettingsController(ChatSettings settings, ChatModelProvider provider) {
        this.settings = settings;
        this.provider = provider;
    }

    /**
     * 当前生效的配置（密钥打码）
     */
    @GetMapping
    public Map<String, Object> read() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("apiKeyMasked", ChatSettings.mask(settings.getApiKey()));
        data.put("apiKeyConfigured", settings.getApiKey() != null && !settings.getApiKey().isBlank());
        data.put("baseUrl", settings.getBaseUrl());
        data.put("model", settings.getModel());
        data.put("temperature", settings.getTemperature());
        data.put("envApiKeyMasked", ChatSettings.mask(settings.getEnvApiKey()));
        data.put("envBaseUrl", settings.getEnvBaseUrl());
        data.put("envModel", settings.getEnvModel());
        return data;
    }

    /**
     * 试连：用请求里的参数（缺省时用当前生效值）发一次极短的请求
     */
    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = resolve(body, false);
        return probe(params);
    }

    /**
     * 保存：先试连，通了才落盘并重建模型
     */
    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        Map<String, Object> params = resolve(body, true);
        String apiKey = (String) params.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            return fail("API Key 不能为空");
        }
        Map<String, Object> probe = probe(params);
        if (!Boolean.TRUE.equals(probe.get("ok"))) {
            probe.put("saved", false);
            return probe;
        }
        settings.apply(apiKey, (String) params.get("baseUrl"), (String) params.get("model"), (Double) params.get("temperature"));
        provider.rebuild();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("saved", true);
        data.put("message", "已保存，当前模型 " + settings.getModel() + "（" + settings.getBaseUrl() + "）");
        data.put("apiKeyMasked", ChatSettings.mask(settings.getApiKey()));
        data.put("baseUrl", settings.getBaseUrl());
        data.put("model", settings.getModel());
        data.put("temperature", settings.getTemperature());
        data.put("latencyMs", probe.get("latencyMs"));
        return data;
    }

    /**
     * 恢复默认：退回启动时的环境变量配置
     */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        settings.reset();
        provider.rebuild();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        String message = settings.getApiKey() == null || settings.getApiKey().isBlank()
                ? "已清空本机保存的配置，但启动时没有设置环境变量，现在没有可用的 API Key，请填一个再保存"
                : "已恢复为启动时的配置";
        data.put("message", message);
        data.put("apiKeyMasked", ChatSettings.mask(settings.getApiKey()));
        data.put("baseUrl", settings.getBaseUrl());
        data.put("model", settings.getModel());
        data.put("temperature", settings.getTemperature());
        return data;
    }

    /**
     * 请求里的值优先；保存时必须显式给 key，试连可以只用当前值
     */
    private Map<String, Object> resolve(Map<String, Object> body, boolean requireBody) {
        Map<String, Object> params = new LinkedHashMap<>();
        Object rawKey = body == null ? null : body.get("apiKey");
        String apiKey = rawKey instanceof String s && !s.isBlank() ? s.trim() : settings.getApiKey();
        if (requireBody && body != null && Boolean.TRUE.equals(body.get("keepCurrentKey"))) {
            apiKey = settings.getApiKey();
        }
        Object rawBaseUrl = body == null ? null : body.get("baseUrl");
        String baseUrl = rawBaseUrl instanceof String s && !s.isBlank() ? s.trim() : settings.getBaseUrl();
        Object rawModel = body == null ? null : body.get("model");
        String model = rawModel instanceof String s && !s.isBlank() ? s.trim() : settings.getModel();
        Object rawTemp = body == null ? null : body.get("temperature");
        double temperature = rawTemp instanceof Number n ? n.doubleValue() : settings.getTemperature();
        params.put("apiKey", apiKey);
        params.put("baseUrl", baseUrl.replaceAll("/+$", ""));
        params.put("model", model);
        params.put("temperature", temperature);
        return params;
    }

    /**
     * 真发一次请求验证连通性。错误信息直接回给前端，方便自己看是 key 错还是地址错
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> probe(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            var probeModel = ChatModelProvider.build(
                    (String) params.get("apiKey"),
                    (String) params.get("baseUrl"),
                    (String) params.get("model"),
                    (Double) params.get("temperature"),
                    8);
            ChatResponse response = probeModel.call(new Prompt("ping"));
            String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", true);
            data.put("latencyMs", System.currentTimeMillis() - start);
            data.put("reply", text == null ? "" : text.trim());
            data.put("message", "连接正常，" + (System.currentTimeMillis() - start) + " ms");
            return data;
        } catch (Exception e) {
            log.warn("试连失败：{}", ModelErrors.rootMessage(e));
            Map<String, Object> data = fail(ModelErrors.describe(e));
            data.put("latencyMs", System.currentTimeMillis() - start);
            return data;
        }
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", false);
        data.put("message", message);
        return data;
    }

}
