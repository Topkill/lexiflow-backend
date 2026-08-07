package com.lexiflow.user.domain;

/**
 * AI Key 使用模式枚举。
 *
 * <p>控制用户调用 AI 功能时使用的 API Key 来源：公共 Key 由系统统一管理，私有 Key 由用户自行提供。</p>
 */
public enum AiKeyMode {
    /** 公共模式，使用系统统一配置的 API Key */
    PUBLIC,
    /** 私有模式，使用用户自行配置的 API Key */
    PRIVATE
}
