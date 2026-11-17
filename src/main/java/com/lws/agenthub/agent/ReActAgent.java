package com.lws.agenthub.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true 表示需要，false 表示这一轮直接作答
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：先思考，需要动手才行动
     * <p>
     * 注意：不需要行动时返回的是模型这一轮的真实回答，而不是一句占位的
     * 「思考完成 - 无需行动」。否则用户问「你好」会收到一串没有任何内容的步骤记录。
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            setActionRequired(shouldAct);
            if (!shouldAct) {
                // 模型这一轮不需要工具，把它说的话原样交给调用方
                String thought = getThought();
                return thought == null || thought.isBlank() ? "这一轮没有需要执行的动作。" : thought;
            }
            return act();
        } catch (Exception e) {
            log.error("步骤执行失败", e);
            setActionRequired(false);
            return "步骤执行失败：" + e.getMessage();
        }
    }
}
