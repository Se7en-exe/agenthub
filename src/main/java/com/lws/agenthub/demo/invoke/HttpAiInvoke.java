package com.lws.agenthub.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONArray;

/**
 * HTTP 方式调用 AI（DeepSeek，OpenAI 兼容协议）
 */
public class HttpAiInvoke {

    public static void main(String[] args) {
        // API密钥
        String apiKey = TestApiKey.API_KEY;

        // 构建请求URL（DeepSeek 兼容 OpenAI 协议）
        String url = "https://api.deepseek.com/chat/completions";

        // 组装 messages 数组
        JSONArray messages = JSONUtil.createArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", "You are a helpful assistant.");
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", "你是谁？");
        messages.add(systemMessage);
        messages.add(userMessage);

        // 构建完整请求体
        JSONObject requestJson = new JSONObject();
        requestJson.set("model", "deepseek-flash");
        requestJson.set("messages", messages);

        // 发送请求
        String result = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestJson.toString())
                .execute()
                .body();

        // 输出结果
        System.out.println(result);
    }
}