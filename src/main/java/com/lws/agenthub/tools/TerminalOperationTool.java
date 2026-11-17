package com.lws.agenthub.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具
 * <p>
 * 几个要点：
 * 1. 子进程输出不能写死字符集。Windows 的 cmd 内部命令（dir、chcp 等）吐 GBK，
 *    而 curl / python 拉回来的 JSON、Git 输出又是 UTF-8，写死哪个都会让另一类变乱码。
 *    这里先按 UTF-8 严格解码，解不动才回退 GBK。
 * 2. stderr 合并进 stdout，不然编译报错这类信息会整个丢掉。
 * 3. 有超时、输出上限、危险命令拦截。
 */
@Slf4j
public class TerminalOperationTool {

    /** 单条命令最长执行时间 */
    private static final long TIMEOUT_SECONDS = 20L;

    /** 最多读多少原始字节，防止一条命令把内存打满 */
    private static final int MAX_BYTES = 200_000;

    /** 最多返回多少字符，避免把模型上下文撑爆 */
    private static final int MAX_OUTPUT_CHARS = 4000;

    private static final Charset GBK = Charset.forName("GBK");

    /** 明显具有破坏性的命令片段，直接拒绝执行 */
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "rm -rf /", "rm -rf /*", "format ", "mkfs", "shutdown", "reboot",
            "del /f /s /q c:", "rd /s /q c:", "reg delete", "diskpart",
            "dd if=/dev/zero", ":(){:|:&};:"
    );

    @Tool(description = "Execute a shell command in the project working directory and return its combined stdout and stderr. "
            + "Use it for listing files, reading text, checking versions, running builds, or calling an HTTP API with curl. "
            + "Commands are capped at " + TIMEOUT_SECONDS + " seconds and the output is truncated if too long.")
    public String executeTerminalCommand(
            @ToolParam(description = "The command line to execute, for example: ls -la or node -v") String command) {

        if (command == null || command.isBlank()) {
            return "Error: empty command.";
        }

        String normalized = command.toLowerCase();
        for (String blocked : BLOCKED_PATTERNS) {
            if (normalized.contains(blocked)) {
                log.warn("blocked destructive command: {}", command);
                return "Refused: this command matches a blocked destructive pattern (" + blocked
                        + "). Nothing was executed.";
            }
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.redirectErrorStream(true);
            process = builder.start();

            final Process p = process;
            // 读取放在另一个线程，主线程才能按时限 kill 掉卡住的命令
            CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
                try (InputStream in = p.getInputStream()) {
                    return in.readNBytes(MAX_BYTES);
                } catch (IOException e) {
                    return new byte[0];
                }
            });

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String body;
            try {
                body = decode(reader.get(3, TimeUnit.SECONDS));
            } catch (Exception e) {
                body = "";
            }

            StringBuilder output = new StringBuilder(sanitize(body));
            if (!finished) {
                process.destroyForcibly();
                output.append("\n[timeout] command exceeded ").append(TIMEOUT_SECONDS)
                        .append("s and was killed.");
            } else {
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    output.append("\n[exit code: ").append(exitCode).append(']');
                }
            }
            return truncate(output.toString().trim());
        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return "Error: command interrupted.";
        }
    }

    /**
     * 先按 UTF-8 严格解码；遇到非法字节再整体回退 GBK。
     * <p>
     * 不用 new String(bytes, UTF_8)：那个会把非法字节替换成 U+FFFD 而不报错，
     * 就失去了"判断到底是不是 UTF-8"的能力。
     */
    static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
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

    /** 去掉 ANSI 转义序列和控制字符，统一换行 */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    static String truncate(String text) {
        if (text.isEmpty()) {
            return "(command produced no output)";
        }
        if (text.length() > MAX_OUTPUT_CHARS) {
            return text.substring(0, MAX_OUTPUT_CHARS)
                    + "\n...[已截断，原始 " + text.length() + " 字符]";
        }
        return text;
    }
}
