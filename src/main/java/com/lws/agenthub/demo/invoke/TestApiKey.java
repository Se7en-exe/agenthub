package com.lws.agenthub.demo.invoke;

/**
 * 仅用于测试获取 API Key
 */
public interface TestApiKey {

    // 修改为你的 DeepSeek API Key，或直接读环境变量
    String API_KEY = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "sk-please-set-env");
}