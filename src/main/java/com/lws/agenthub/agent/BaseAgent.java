package com.lws.agenthub.agent;

import cn.hutool.core.util.StrUtil;
import com.lws.agenthub.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现 step 方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    /** 代理名称 */
    private String name;

    /** 系统提示词 */
    private String systemPrompt;

    /** 每一步追加的提示词 */
    private String nextStepPrompt;

    /** 代理状态 */
    private AgentState state = AgentState.IDLE;

    /** 当前第几步 */
    private int currentStep = 0;

    /** 最大步数，兜底防死循环 */
    private int maxSteps = 10;

    /** 大模型客户端 */
    private ChatClient chatClient;

    /** 会话上下文（自己维护，不用框架的 memory） */
    private List<Message> messageList = new ArrayList<>();

    /** 最近一轮模型的思考文本，供 step() 或外部展示用 */
    private String thought;

    /** 最近一轮是否需要调用工具；false 表示模型直接给了答案 */
    private boolean actionRequired = false;

    /**
     * 运行代理（阻塞版）
     *
     * @param userPrompt 用户输入
     * @return 执行过程记录
     */
    public String run(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                String stepResult = step();
                results.add("Step " + stepNumber + ": " + stepResult);
                // 模型这一轮没有调用工具，说明它已经把话说完了，不必继续追问
                if (!actionRequired) {
                    state = AgentState.FINISHED;
                }
            }
            if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误：" + e.getMessage();
        } finally {
            cleanup();
        }
    }

    /**
     * 运行代理（SSE 流式版）
     *
     * @param userPrompt 用户输入
     * @return SSE 发射器
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
                return;
            }

            this.state = AgentState.RUNNING;
            messageList.add(new UserMessage(userPrompt));

            try {
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);
                    String stepResult = step();
                    if (actionRequired) {
                        // 这一轮真的动了工具，带上步骤号方便区分
                        sseEmitter.send("Step " + stepNumber + ": " + stepResult);
                    } else {
                        // 这一轮是模型直接作答，原样输出，不加任何前缀
                        sseEmitter.send(stepResult);
                        state = AgentState.FINISHED;
                    }
                }
                if (currentStep >= maxSteps && state != AgentState.FINISHED) {
                    state = AgentState.FINISHED;
                    sseEmitter.send("执行结束：达到最大步骤（" + maxSteps + "）");
                }
                state = AgentState.FINISHED;
                // 显式发结束标记，前端收到后主动关闭，避免把正常收流当成连接异常
                sseEmitter.send("[DONE]");
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     */
    public abstract String step();

    /**
     * 清理资源，子类可重写
     */
    protected void cleanup() {
    }
}
