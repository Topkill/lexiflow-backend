package com.lexiflow.ai.prompt.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 提示词模板实体
 * <p>
 * 存储用户自定义的 AI 提示词模板，包含系统提示词、规则提示词、输出 JSON Schema 等。
 * 支持按功能类型和词书范围关联，支持乐观锁版本控制，可来源于其他模板复制或内置模板。
 * </p>
 */
@Getter
@Setter
@TableName("ai_prompt_template")
public class AiPromptTemplate {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 功能类型 */
    private AiPromptFeatureType featureType;
    /** 生效词书 ID，0 表示全部词书 */
    private Long wordbookId;
    /** 模板名称 */
    private String name;
    /** 系统提示词 */
    private String systemPrompt;
    /** 规则提示词 */
    private String instructionPrompt;
    /** 输出 JSON Schema */
    private String outputSchemaJson;
    /** 是否启用 */
    private Boolean enabled;
    /** 来源模板 ID（复制自） */
    private Long sourceTemplateId;
    /** 来源内置模板键 */
    private String sourceBuiltinKey;
    /** 创建人 ID */
    private Long createdBy;
    /** 更新人 ID */
    private Long updatedBy;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 逻辑删除标志 */
    @TableLogic
    private Integer deleted;
    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
