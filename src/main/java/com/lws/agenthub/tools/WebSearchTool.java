package com.lws.agenthub.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    // 单次搜索结果最多返回的条数
    static final int MAX_RESULT_SIZE = 5;

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            // 取出返回结果的前 5 条（安全截断，结果不足或为空都不会越界）
            List<Object> objects = pickTopResults(organicResults, MAX_RESULT_SIZE);
            if (objects.isEmpty()) {
                return "No search result found for: " + query;
            }
            // 拼接搜索结果为字符串
            return objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }

    /**
     * 从搜索结果里安全地取前 limit 条。
     *
     * <p>原实现直接写 organicResults.subList(0, 5)，当接口返回的结果少于 5 条
     * （弱网、冷门关键词、接口降级时很常见）会抛 IndexOutOfBoundsException，
     * 整个工具调用中断。这里改成按实际条数与 limit 的最小值截断，并对 null 兜底。
     *
     * @param results 搜索结果数组，允许为 null
     * @param limit   最多取几条，小于等于 0 时返回空列表
     * @return 截断后的结果列表，永不为 null
     */
    static List<Object> pickTopResults(JSONArray results, int limit) {
        if (results == null || results.isEmpty() || limit <= 0) {
            return List.of();
        }
        return results.subList(0, Math.min(limit, results.size()));
    }
}
