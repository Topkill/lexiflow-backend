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

/**
 * 单词 AI 问答缓存实体
 * <p>
 * 存储 AI 生成的单词问答结果，支持缓存复用机制。
 * 通过 cacheKey 标识相同问题，cacheActive 标记当前活跃缓存，
 * hitCount 记录缓存命中次数，用于缓存淘汰策略。
 * </p>
 */
@Getter
@Setter
@TableName("word_ai_qa")
public class WordAiQa {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建者用户 ID */
    private Long createdByUserId;
    /** 单词 ID */
    private Long wordId;
    /** 词库 ID */
    private Long wordbookId;
    /** 用户问题 */
    private String question;
    /** 源数据哈希，用于检测上下文变化 */
    private String sourceHash;
    /** 缓存键，用于快速查找活跃缓存 */
    private String cacheKey;
    /** AI 生成的 JSON 内容 */
    private String contentJson;
    /** 输出 JSON Schema */
    private String outputSchemaJson;
    /** 是否为活跃缓存 */
    private Boolean cacheActive;
    /** 缓存命中次数 */
    private Integer hitCount;
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
