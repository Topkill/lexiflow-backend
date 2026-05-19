package com.lexiflow.ai.content.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_content_cache")
public class AiContentCache {

    @TableId(type = IdType.AUTO)
    private Long id;

    private AiContentType contentType;
    private String cacheKey;
    private Long userId;
    private Long wordId;
    private Long wordbookId;
    private String sourceHash;
    private String contentJson;
    private String markdownContent;
    private String modelName;
    private LocalDateTime expiresAt;
    private Integer hitCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
