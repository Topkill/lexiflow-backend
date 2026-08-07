package com.lexiflow.system.config.domain;

/**
 * 系统配置值类型枚举。
 * <p>定义配置项支持的值类型，服务层会根据类型对输入值进行规范化校验。</p>
 */
public enum SystemConfigValueType {
    /** 字符串 */
    STRING,
    /** 数字 */
    NUMBER,
    /** 布尔值 */
    BOOLEAN,
    /** JSON 对象 */
    JSON
}
