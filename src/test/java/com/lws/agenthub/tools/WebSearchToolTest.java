package com.lws.agenthub.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * WebSearchTool.pickTopResults 的单元测试。
 *
 * <p>覆盖原实现 organicResults.subList(0, 5) 会越界的场景：
 * 结果为空、结果少于 5 条、结果正好 5 条、结果多于 5 条、以及入参为 null。
 * 这些用例不需要真实调用 SearchAPI，也不依赖任何 API Key。
 */
class WebSearchToolTest {

    /** 造一个 size 条结果的 JSONArray，每条带 title 字段 */
    private static JSONArray buildResults(int size) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < size; i++) {
            JSONObject item = new JSONObject();
            item.set("title", "result-" + i);
            array.add(item);
        }
        return array;
    }

    @Test
    void nullResultsReturnEmptyList() {
        List<Object> picked = WebSearchTool.pickTopResults(null, WebSearchTool.MAX_RESULT_SIZE);
        Assertions.assertTrue(picked.isEmpty(), "null 结果应返回空列表");
    }

    @Test
    void emptyResultsReturnEmptyList() {
        List<Object> picked = WebSearchTool.pickTopResults(buildResults(0), WebSearchTool.MAX_RESULT_SIZE);
        Assertions.assertTrue(picked.isEmpty(), "空结果应返回空列表");
    }

    /** 原实现在这里抛 IndexOutOfBoundsException：只有 3 条却要取 5 条 */
    @Test
    void fewerThanLimitDoesNotThrow() {
        List<Object> picked = WebSearchTool.pickTopResults(buildResults(3), WebSearchTool.MAX_RESULT_SIZE);
        Assertions.assertEquals(3, picked.size(), "只有 3 条时应全部返回");
    }

    /** 边界：正好等于上限 */
    @Test
    void exactlyLimitReturnsAll() {
        List<Object> picked = WebSearchTool.pickTopResults(buildResults(5), WebSearchTool.MAX_RESULT_SIZE);
        Assertions.assertEquals(5, picked.size(), "正好 5 条时应返回 5 条");
    }

    /** 边界：多于上限，只取前 5 条 */
    @Test
    void moreThanLimitTruncates() {
        List<Object> picked = WebSearchTool.pickTopResults(buildResults(8), WebSearchTool.MAX_RESULT_SIZE);
        Assertions.assertEquals(5, picked.size(), "多于 5 条时应截断为 5 条");
        Assertions.assertEquals("result-0", ((JSONObject) picked.get(0)).getStr("title"), "应保留第一条");
        Assertions.assertEquals("result-4", ((JSONObject) picked.get(4)).getStr("title"), "第 5 条应是 result-4");
    }

    /** 边界：limit 非正整数 */
    @Test
    void nonPositiveLimitReturnsEmptyList() {
        Assertions.assertTrue(WebSearchTool.pickTopResults(buildResults(8), 0).isEmpty());
        Assertions.assertTrue(WebSearchTool.pickTopResults(buildResults(8), -1).isEmpty());
    }
}
