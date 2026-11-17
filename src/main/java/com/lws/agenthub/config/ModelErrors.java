package com.lws.agenthub.config;

/**
 * 把大模型供应商抛的英文异常翻成一句能直接给用户看的中文。
 * <p>
 * 单独抽出来是因为三处都要用：智能体的 think()、知识库助手的 RAG 流、
 * 以及设置页的试连。以前 think() 把异常连着堆栈一起吞掉，界面上只剩
 * 一句「这一轮没有需要执行的动作」，用户完全看不出是 key 的问题。
 */
public final class ModelErrors {

    private ModelErrors() {
    }

    /**
     * 取最内层异常的信息，避免把 Spring 的包装异常名糊到界面上
     */
    public static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /**
     * 按状态码翻成可执行的一句话
     */
    public static String describe(Throwable e) {
        String message = rootMessage(e);
        String lower = message.toLowerCase();
        if (lower.contains("401") || lower.contains("invalidapikey") || lower.contains("invalid api key")
                || lower.contains("authentication")) {
            return "API Key 无效或未配置，点右上角「模型设置」填一个可用的 key（401）";
        }
        if (lower.contains("402") || lower.contains("insufficient") || lower.contains("balance")) {
            return "账户余额不足，请到服务商后台充值（402）";
        }
        if (lower.contains("403")) {
            return "这个 key 无权调用当前模型，或模型名写错了（403）";
        }
        if (lower.contains("404") || lower.contains("not found")) {
            return "接口地址或模型名不对（404），到「模型设置」里核对 Base URL 与模型名";
        }
        if (lower.contains("429")) {
            return "请求太频繁，稍等几秒重试（429）";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "连不上模型服务，检查网络和 Base URL 是否可达（超时）";
        }
        if (lower.contains("connection refused") || lower.contains("unknownhost") || lower.contains("resolve")) {
            return "连不上这个地址，确认 Base URL 拼写正确、本机能访问外网";
        }
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
