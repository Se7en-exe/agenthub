package com.lws.agenthub.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 大模型连接配置：密钥、服务地址、模型名、温度。
 * <p>
 * 取值顺序是「上次在页面上保存的值」高于「启动时的环境变量」，
 * 保存过的配置落在 user.dir/tmp/chat-settings.json，重启也还在。
 * 之所以不直接改 application.yml，是因为 yml 打包进 jar 后是只读资源，
 * 而这里要支持运行期改。
 */
@Component
@Slf4j
public class ChatSettings {

    /** 配置文件路径；和 FileConstant 一样挂在启动目录下，便于查看和删除 */
    private static final Path STORE = Paths.get(System.getProperty("user.dir"), "tmp", "chat-settings.json");

    /** 环境变量没配时 application.yml 会留下这个占位符，视同未配置 */
    private static final String PLACEHOLDER = "sk-please-set-env";

    private final String envApiKey;
    private final String envBaseUrl;
    private final String envModel;
    private final double envTemperature;

    private volatile String apiKey;
    private volatile String baseUrl;
    private volatile String model;
    private volatile double temperature;

    public ChatSettings(@Value("${spring.ai.openai.api-key:}") String apiKey,
                        @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
                        @Value("${spring.ai.openai.chat.options.model:deepseek-flash}") String model,
                        @Value("${spring.ai.openai.chat.options.temperature:0.7}") double temperature) {
        this.envApiKey = normalizeKey(apiKey);
        this.envBaseUrl = baseUrl;
        this.envModel = model;
        this.envTemperature = temperature;
        this.apiKey = envApiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        loadFromDisk();
        log.info("大模型配置：baseUrl={} model={} apiKey={}", baseUrl, model, mask(apiKey));
    }

    /**
     * 把配置里的占位符、空白统一当成「没配」
     */
    private static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return PLACEHOLDER.equals(trimmed) ? "" : trimmed;
    }

    /**
     * 打码展示。只保留前 6 位和后 4 位，长度不够就整体打码
     */
    public static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 12) {
            return "****";
        }
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }

    private synchronized void loadFromDisk() {
        if (!Files.isRegularFile(STORE)) {
            return;
        }
        try {
            JSONObject json = JSONUtil.readJSONObject(STORE.toFile(), StandardCharsets.UTF_8);
            String savedKey = normalizeKey(json.getStr("apiKey"));
            // 只覆盖有值且不为空的项，避免把环境变量里的配置冲掉
            if (!savedKey.isBlank()) {
                this.apiKey = savedKey;
            }
            String savedBaseUrl = json.getStr("baseUrl");
            if (savedBaseUrl != null && !savedBaseUrl.isBlank()) {
                this.baseUrl = savedBaseUrl;
            }
            String savedModel = json.getStr("model");
            if (savedModel != null && !savedModel.isBlank()) {
                this.model = savedModel;
            }
            Double savedTemp = json.getDouble("temperature");
            if (savedTemp != null) {
                this.temperature = savedTemp;
            }
            log.info("已加载本地保存的大模型配置：{}", json);
        } catch (Exception e) {
            log.warn("读取 {} 失败，继续使用环境变量里的配置：{}", STORE, e.getMessage());
        }
    }

    private synchronized void saveToDisk() {
        try {
            Files.createDirectories(STORE.getParent());
            JSONObject json = new JSONObject();
            json.set("apiKey", apiKey);
            json.set("baseUrl", baseUrl);
            json.set("model", model);
            json.set("temperature", temperature);
            Files.writeString(STORE, json.toStringPretty(), StandardCharsets.UTF_8);
            log.info("大模型配置已保存到 {}", STORE);
        } catch (IOException e) {
            log.warn("保存大模型配置失败：{}", e.getMessage());
        }
    }

    /**
     * 应用一组新配置并落盘
     */
    public synchronized void apply(String newApiKey, String newBaseUrl, String newModel, Double newTemperature) {
        if (newApiKey != null && !newApiKey.isBlank()) {
            this.apiKey = newApiKey.trim();
        }
        if (newBaseUrl != null && !newBaseUrl.isBlank()) {
            this.baseUrl = newBaseUrl.trim().replaceAll("/+$", "");
        }
        if (newModel != null && !newModel.isBlank()) {
            this.model = newModel.trim();
        }
        if (newTemperature != null) {
            this.temperature = Math.max(0.0, Math.min(2.0, newTemperature));
        }
        saveToDisk();
    }

    /**
     * 回退到启动时的环境变量配置，并清掉本地文件
     */
    public synchronized void reset() {
        this.apiKey = envApiKey;
        this.baseUrl = envBaseUrl;
        this.model = envModel;
        this.temperature = envTemperature;
        try {
            Files.deleteIfExists(STORE);
        } catch (IOException e) {
            log.warn("删除 {} 失败：{}", STORE, e.getMessage());
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getEnvApiKey() {
        return envApiKey;
    }

    public String getEnvBaseUrl() {
        return envBaseUrl;
    }

    public String getEnvModel() {
        return envModel;
    }

    public double getEnvTemperature() {
        return envTemperature;
    }
}
