package com.lws.agenthub.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class KnowledgeAssistantTest {

    @Resource
    private KnowledgeAssistant knowledgeAssistant;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是 Se7en";
        String answer = knowledgeAssistant.doChat(message, chatId);
        // 第二轮
        message = "企业版和标准版有什么区别";
        answer = knowledgeAssistant.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我的另一半叫什么来着？刚跟你说过，帮我回忆一下";
        answer = knowledgeAssistant.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是 Se7en，我想知道企业版和标准版的差别，但我不知道该看哪些点";
        KnowledgeAssistant.AnswerDigest digest = knowledgeAssistant.doChatWithReport(message, chatId);
        Assertions.assertNotNull(digest);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "申请退款之后多久能到账？";
        String answer = knowledgeAssistant.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("Spring AI 1.0 里 ToolCallingManager 的作用是什么？");

        // 测试网页抓取：抓一份公开文档回来做摘要
        testMessage("抓一下 https://docs.spring.io/spring-ai/reference/ 首页，总结这个框架能做什么");

        // 测试资源下载：图片下载
        testMessage("下载一张技术架构示意图到本地文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本统计当前目录下 java 文件的行数");

        // 测试文件操作：保存用户档案
        testMessage("把这段问答记录保存成文件");

        // 测试 PDF 生成
        testMessage("生成一份‘知识库上线检查清单’PDF，包含数据准备、切分策略、评测、灰度四部分");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = knowledgeAssistant.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP
//        String message = "帮我在广州找几个能打印装订的地方";
//        String answer =  knowledgeAssistant.doChatWithMcp(message, chatId);
//        Assertions.assertNotNull(answer);
        // 测试图片搜索 MCP
        String message = "帮我搜索一些系统架构图";
        String answer =  knowledgeAssistant.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
