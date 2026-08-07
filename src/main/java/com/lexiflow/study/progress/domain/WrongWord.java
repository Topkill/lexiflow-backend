package com.lexiflow.study.progress.domain;

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
 * 错词记录实体。
 * <p>记录用户在学习过程中答错的单词，用于错词加练和复习。
 * 当用户回答“不认识”时会创建或更新错词记录，回答“认识”时会标记为已解决。</p>
 */
@Getter
@Setter
@TableName("wrong_word")
public class WrongWord {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;
    /** 词书 ID */
    private Long wordbookId;
    /** 单词 ID */
    private Long wordId;
    /** 累计错误次数 */
    private Integer wrongCount;
    /** 最后一次出错的学习场景 */
    private StudyScene lastSource;
    /** 最后一次出错关联的学习事件 ID */
    private Long lastEventId;
    /** 最后一次出错时间 */
    private LocalDateTime lastWrongAt;
    /** 是否已解决（用户答对后标记为已解决） */
    private Boolean resolved;
    /** 解决时间 */
    private LocalDateTime resolvedAt;
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
