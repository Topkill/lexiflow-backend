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

@Getter
@Setter
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private AiConfigScope configScope;
    private AiContentType contentType;
    private String modelName;
    private String apiBaseUrl;
    private String requestHash;
    private AiCallStatus status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer latencyMs;
    private String errorCode;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
