package com.lws.agenthub.controller;

import com.lws.agenthub.rag.KnowledgeBaseService;
import com.lws.agenthub.rag.KnowledgeRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口：上传、列表、删除
 */
@RestController
@RequestMapping("/ai/knowledge")
@Slf4j
public class KnowledgeController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeRegistry knowledgeRegistry;

    /**
     * 列出知识库里现有的文档
     */
    @GetMapping("/list")
    public Map<String, Object> list() {
        knowledgeBaseService.warmup();
        List<Map<String, Object>> files = knowledgeRegistry.summary();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("files", files);
        result.put("count", files.size());
        result.put("chunks", knowledgeRegistry.totalChunks());
        return result;
    }

    /**
     * 上传一个文档并入知识库，上传后立刻可被检索到
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        // 先把懒加载的向量库建起来，否则内置文档还没登记，同名判断会漏
        knowledgeBaseService.warmup();
        Map<String, Object> data = knowledgeBaseService.ingest(file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("data", data);
        result.put("total", knowledgeRegistry.totalChunks());
        return result;
    }

    /**
     * 从知识库删除一个文档
     */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam("name") String name) {
        int removed = knowledgeBaseService.remove(name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("removed", removed);
        result.put("total", knowledgeRegistry.totalChunks());
        return result;
    }
}
