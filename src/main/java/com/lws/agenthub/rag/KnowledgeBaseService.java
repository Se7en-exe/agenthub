package com.lws.agenthub.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库文档入库/删除/查询
 *
 * <p>支持 md、txt、pdf 三种格式。入库流程：解析成纯文本 → 按 token 切片 →
 * 写入内存向量库（向量由本地 ONNX 模型算，不联网不产生调用费）→ 登记文件名到 id 的映射。
 */
@Service
@Slf4j
public class KnowledgeBaseService {

    private static final Set<String> TEXT_EXT = Set.of("md", "txt", "markdown", "log", "csv");
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Charset GBK = Charset.forName("GBK");

    @Resource
    private VectorStore knowledgeVectorStore;

    @Resource
    private KnowledgeRegistry knowledgeRegistry;

    /** 每次切片 300 token、重叠 100，够保留上下文又不至于切得太碎 */
    private final TokenTextSplitter splitter = new TokenTextSplitter(300, 100, 5, 10000, true);

    /**
     * 上传一个文件并入库
     *
     * @return 摘要信息：文件名、切片数、总字符数
     */
    public Map<String, Object> ingest(MultipartFile file) throws IOException {
        String filename = sanitizeName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件是空的");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("文件超过 20MB，请先拆分");
        }
        if (knowledgeRegistry.idsOf(filename).size() > 0) {
            throw new IllegalArgumentException("知识库里已经有同名文件「" + filename + "」，请换个名字或先删除旧的");
        }

        String ext = extensionOf(filename);
        List<Document> raw;
        if ("pdf".equals(ext)) {
            List<Document> pages = new PagePdfDocumentReader(new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).get();
            raw = new ArrayList<>();
            for (Document page : pages) {
                String text = squeeze(page.getText());
                if (!text.isBlank()) {
                    raw.add(new Document(text));
                }
            }
        } else if (TEXT_EXT.contains(ext)) {
            String text = decode(file.getBytes());
            if (text.isBlank()) {
                throw new IllegalArgumentException("文件里没有可读文本");
            }
            raw = List.of(new Document(text));
        } else {
            throw new IllegalArgumentException("暂不支持 ." + ext + " 格式，请上传 md / txt / pdf");
        }

        // 切片，并把来源信息塞进每个切片的元数据，方便回答时标注出处
        String category = filename.contains(" - ")
                ? filename.substring(filename.lastIndexOf(" - ") + 3).replaceAll("\\.[^.]+$", "")
                : "上传文档";
        List<Document> chunks = new ArrayList<>();
        for (Document d : raw) {
            for (Document piece : splitter.apply(List.of(new Document(d.getText())))) {
                Map<String, Object> md = new HashMap<>();
                md.put("filename", filename);
                md.put("category", category);
                md.put("source", "upload");
                chunks.add(new Document(piece.getText(), md));
            }
        }

        knowledgeVectorStore.add(chunks);
        List<String> ids = chunks.stream().map(Document::getId).toList();
        knowledgeRegistry.register(filename, ids, "upload");
        int chars = chunks.stream().mapToInt(d -> d.getText().length()).sum();
        log.info("知识库新增文件 {}，切片 {} 片，共 {} 字符", filename, chunks.size(), chars);

        Map<String, Object> result = new HashMap<>();
        result.put("name", filename);
        result.put("chunks", chunks.size());
        result.put("chars", chars);
        return result;
    }

    /**
     * 从知识库删除一个文件
     *
     * @return 被删掉的切片数
     */
    public int remove(String filename) {
        List<String> ids = knowledgeRegistry.idsOf(filename);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("知识库里没有「" + filename + "」");
        }
        knowledgeVectorStore.delete(ids);
        knowledgeRegistry.remove(filename);
        log.info("知识库删除文件 {}，移除切片 {} 片", filename, ids.size());
        return ids.size();
    }

    /**
     * 触发一次检索，逼懒加载的向量库完成初始化（顺带把内置文档登记进登记表）
     */
    public void warmup() {
        if (knowledgeRegistry.isReady() || !knowledgeRegistry.tryStartWarmup()) {
            return;
        }
        try {
            knowledgeVectorStore.similaritySearch("初始化知识库");
            knowledgeRegistry.markReady();
        } catch (Exception e) {
            knowledgeRegistry.clearWarming();
            log.warn("知识库初始化失败：{}", e.getMessage());
        }
    }

    /**
     * PDF 抽取出来的文本按字符间距塞满了空格，中文会被拆成「项 目 经 历」。
     * 这里把连续空白压成一个空格、去掉 ASCII 字符之间多余的空格，长度能压掉七成，
     * 切片时就不会把预算浪费在空白上。
     */
    static String squeeze(String text) {
        if (text == null) {
            return "";
        }
        return text
                // 行内连续空白压成一个空格
                .replaceAll("[ \\t\\u00A0]+", " ")
                // 中文之间不该有空格，直接删掉
                .replaceAll("(?<=[\\u4e00-\\u9fff]) (?=[\\u4e00-\\u9fff])", "")
                // 三行以上空行压成两行
                .replaceAll("(\\r?\\n){3,}", "\n\n")
                .trim();
    }

    /** 去掉路径部分，只留文件名，防止有人传 ../../ 这种东西 */
    private String sanitizeName(String original) {
        String name = (original == null || original.isBlank()) ? "未命名文档" : original;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    /**
     * 文本文件编码不靠猜：先按 UTF-8 严格解码，遇到非法字节再回退 GBK。
     * 直接 new String(bytes, UTF_8) 会把非法字节悄悄换成 U+FFFD，就分不清到底是哪种编码了。
     */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, GBK);
        }
    }
}
