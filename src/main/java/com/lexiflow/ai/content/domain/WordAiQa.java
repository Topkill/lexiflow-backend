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
@TableName("word_ai_qa")
public class WordAiQa {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long createdByUserId;
    private Long wordId;
    private Long wordbookId;
    private String question;
    private String sourceHash;
    private String cacheKey;
    private String contentJson;
    private String outputSchemaJson;
    private Boolean cacheActive;
    private Integer hitCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
