package com.lws.agenthub.agent;

import com.lws.agenthub.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * AgentHub 自主智能体：会自己拆步骤、挑工具、看结果，直到判断该收工
 */
@Component
public class AutoAgent extends ToolCallAgent {

    public AutoAgent(ToolCallback[] allTools, ChatModel chatModel) {
        super(allTools);
        this.setName("autoAgent");

        String SYSTEM_PROMPT = """
                You are AutoAgent, an autonomous agent that completes tasks by calling tools.
                You can read and write files, run terminal commands, search the web, scrape web pages,
                download resources and generate PDFs.

                Rules:
                1. Only call a tool when the task genuinely requires touching the outside world.
                2. If the user is just greeting you, asking what you can do, or asking a question you can
                   answer directly from your own knowledge, answer in plain text and do NOT call any tool.
                3. When you do call tools, briefly say what you are about to do and why.
                4. After a tool returns, always tell the user what you found in plain language.
                   If the raw output is machine-readable (JSON, HTML, logs, hex), pull out only the few
                   facts the user asked for. Never paste raw JSON or a whole web page into the reply.
                5. Only call the terminate tool when you have nothing left to tell the user. If you still
                   owe the user an answer, reply in plain text instead - the run ends there too.
                6. Never invent tool output. If a tool fails, say so and try a different approach or stop.
                7. Write in plain conversational text. Do not use Markdown syntax such as **bold**,
                   # headings or | tables. For lists use "1. 2. 3." on separate lines.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                Decide what to do next for the user's current request.

                If the request still needs the outside world (files, shell, network, PDF), call the
                appropriate tool now.

                If the tool results already contain what the user asked for, answer them in plain text
                now - name the concrete facts (titles, numbers, file names), not the raw payload.
                Replying in text ends the run, which is the correct way to finish.

                Do not call a tool merely to have something to do, and do not jump to the terminate
                tool while the user is still waiting for an answer.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
