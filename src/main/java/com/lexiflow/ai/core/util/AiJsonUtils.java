package com.lexiflow.ai.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;

/**
 * AI JSON 工具类
 * <p>
 * 提供从 AI 响应文本中提取 JSON 对象的工具方法。
 * 支持清理  标签、Markdown 代码块等干扰内容，
 * 并通过大括号深度匹配提取完整的 JSON 对象字符串。
 * </p>
 */
public final class AiJsonUtils {

    private AiJsonUtils() {
    }

    /**
     * 解析 AI 响应文本为 JSON 对象
     *
     * @param objectMapper JSON 序列化器
     * @param content AI 响应文本
     * @return 解析后的 JSON 节点
     * @throws BizException 当内容不是合法 JSON 对象或解析失败时
     */
    public static JsonNode parseObject(ObjectMapper objectMapper, String content) {
        try {
            JsonNode node = objectMapper.readTree(extractObjectJson(content));
            if (!node.isObject()) {
                throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 返回内容不是 JSON 对象");
            }
            return node;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 返回内容解析失败");
        }
    }

    /**
     * 从 AI 响应文本中提取 JSON 对象字符串
     * <p>
     * 先清理  标签和 Markdown 代码块，然后通过大括号深度匹配提取完整的 JSON 对象。
     * </p>
     *
     * @param content AI 响应文本
     * @return 提取的 JSON 对象字符串
     */
    public static String extractObjectJson(String content) {
        String text = stripMarkdownFence(stripThinking(content == null ? "" : content.trim()));
        int firstBrace = text.indexOf('{');
        if (firstBrace < 0) {
            return text;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = firstBrace; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(firstBrace, i + 1);
                }
            }
        }
        return text.substring(firstBrace);
    }

    private static String stripThinking(String text) {
        String current = text;
        int endThink = current.lastIndexOf("</think>");
        if (endThink >= 0) {
            current = current.substring(endThink + "</think>".length()).trim();
        }
        return current;
    }

    private static String stripMarkdownFence(String text) {
        String current = text;
        if (current.startsWith("```")) {
            int firstLineBreak = current.indexOf('\n');
            int lastFence = current.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                current = current.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return current;
    }
}
