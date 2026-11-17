package com.lws.agenthub.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库文件登记表
 *
 * <p>SimpleVectorStore 只能按文档 id 删除，而 id 是入库时随机生成的，事后拿不回来。
 * 所以入库的同时把「文件名 → 文档 id 列表」记在这里，删除文件时按文件名查出 id 再删。
 * 这份表只活在内存里，和内存向量库同生共死，重启一起清零，不存在两边不一致的问题。
 */
@Component
public class KnowledgeRegistry {

    /** 文件名 → 该文件切出来的所有文档 id */
    private final Map<String, List<String>> files = new ConcurrentHashMap<>();

    /** 文件名 → 摘要信息，供列表接口展示 */
    private final Map<String, Map<String, Object>> meta = new ConcurrentHashMap<>();

    /** 内置文档（随包分发的 markdown）是否已经登记过 */
    private volatile boolean ready = false;

    /** 正在初始化，避免并发重复触发 */
    private volatile boolean warming = false;

    public void register(String filename, List<String> ids, String source) {
        files.put(filename, new ArrayList<>(ids));
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", filename);
        info.put("chunks", ids.size());
        info.put("source", source);
        meta.put(filename, info);
    }

    public List<String> idsOf(String filename) {
        return files.getOrDefault(filename, List.of());
    }

    public void remove(String filename) {
        files.remove(filename);
        meta.remove(filename);
    }

    public boolean isReady() {
        return ready;
    }

    public void markReady() {
        ready = true;
        warming = false;
    }

    /** 抢到初始化权才去构建，返回 false 表示别人已经在建了 */
    public boolean tryStartWarmup() {
        if (ready || warming) {
            return false;
        }
        warming = true;
        return true;
    }

    public void clearWarming() {
        warming = false;
    }

    public List<Map<String, Object>> summary() {
        List<Map<String, Object>> list = new ArrayList<>(meta.values());
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return list;
    }

    public int totalChunks() {
        return files.values().stream().mapToInt(List::size).sum();
    }
}
