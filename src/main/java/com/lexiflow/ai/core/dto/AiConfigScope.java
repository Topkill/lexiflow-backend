package com.lexiflow.ai.core.dto;

/**
 * AI 配置来源枚举
 * <p>
 * 标识 AI 调用使用的配置来源：系统公共配置或用户私有配置。
 * </p>
 */
public enum AiConfigScope {
    /** 系统公共配置 */
    PUBLIC,
    /** 用户私有配置 */
    PRIVATE
}
