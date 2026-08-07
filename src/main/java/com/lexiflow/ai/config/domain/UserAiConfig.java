package com.lexiflow.ai.config.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户私有 AI 配置实体，对应数据库 {@code user_ai_config} 表。
 *
 * <p>存储用户个人的 AI 服务配置，用于用户使用自己的 API Key 调用 AI 功能。
 * 每个用户最多一条配置记录，API Key 以加密形式存储。</p>
 */
@Getter
@Setter
@TableName("user_ai_config")
public class UserAiConfig {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;
    /** AI 服务 API 基础地址 */
    private String apiBaseUrl;
    /** AES/GCM 加密后的 API Key */
    private String encryptedApiKey;
    /** 模型名称 */
    private String modelName;
    /** 温度参数 */
    private BigDecimal temperature;
    /** 是否启用流式输出 */
    private Boolean streamEnabled;
    /** 是否启用此配置 */
    private Boolean enabled;
    /** 最后一次验证时间 */
    private LocalDateTime lastVerifiedAt;

    /** 创建时间，由 MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间，由 MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
}
