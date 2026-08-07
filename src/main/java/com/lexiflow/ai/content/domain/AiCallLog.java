package com.lexiflow.ai.content.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lexiflow.ai.core.dto.AiConfigScope;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 调用日志实体
 * <p>
 * 记录每次 AI 调用的详细信息，包括调用者、配置来源、内容类型、模型信息、
 * Token 消耗、调用状态、延迟及错误信息等。
 * </p>
 */
@Getter
@Setter
@TableName("ai_call_log")
public class AiCallLog {

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的异步任务 ID */
    private Long asyncTaskId;
    /** 调用用户 ID */
    private Long userId;
    /** AI 配置来源（公共/私有） */
    private AiConfigScope configScope;
    /** AI 内容类型 */
    private AiContentType contentType;
    /** 使用的模型名称 */
    private String modelName;
    /** API 基础地址 */
    private String apiBaseUrl;
    /** 请求内容哈希，用于去重 */
    private String requestHash;
    /** 提示词特性类型 */
    private String promptFeatureType;
    /** 提示词模板 ID */
    private Long promptTemplateId;
    /** 提示词模板名称 */
    private String promptTemplateName;
    /** 调用状态 */
    private AiCallStatus status;
    /** 提示词 Token 数 */
    private Integer promptTokens;
    /** 完成内容 Token 数 */
    private Integer completionTokens;
    /** 总 Token 数 */
    private Integer totalTokens;
    /** 调用延迟（毫秒） */
    private Integer latencyMs;
    /** 错误码 */
    private String errorCode;
    /** 错误信息 */
    private String errorMessage;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
