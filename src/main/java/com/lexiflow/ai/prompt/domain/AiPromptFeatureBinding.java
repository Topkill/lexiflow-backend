package com.lexiflow.ai.prompt.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 提示词功能绑定实体
 * <p>
 * 记录 AI 功能类型与提示词模板的绑定关系，支持按词书范围绑定。
 * 每个功能类型在特定词书范围内只能绑定一个模板。
 * </p>
 */
@Getter
@Setter
@TableName("ai_prompt_feature_binding")
public class AiPromptFeatureBinding {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** AI 功能类型 */
    private AiPromptFeatureType featureType;
    /** 生效词书 ID，0 表示全部词书 */
    private Long wordbookId;
    /** 绑定的模板 ID */
    private Long templateId;
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
}
