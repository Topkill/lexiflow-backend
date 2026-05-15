package com.lexiflow.ai.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;

public final class AiJsonUtils {

    private AiJsonUtils() {
    }

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
