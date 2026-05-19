package com.lexiflow.wordbook.domain;

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
@TableName("word")
public class Word {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long wordbookId;
    private String word;
    private String normalizedWord;
    private String phonetic0;
    private String phonetic1;
    private String trans;
    private String sentences;
    private String phrases;
    private String synos;
    private String relWords;
    private String etymology;
    private String primaryPos;
    private String primaryDefinition;
    private String tags;
    private Integer sequenceNo;
    private Integer difficultyLevel;
    private Integer examFrequency;
    private Boolean enabled;
    private Long createdBy;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
