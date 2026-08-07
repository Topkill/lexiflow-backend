package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 词库内单词查询行 DTO。
 * <p>用于词库内单词分页查询的原始数据行，包含单词的完整信息。</p>
 *
 * @param id 单词ID
 * @param word 单词拼写
 * @param normalizedWord 标准化拼写
 * @param phonetic0 英式音标
 * @param phonetic1 美式音标
 * @param trans 释义（JSON格式）
 * @param sentences 例句（JSON格式）
 * @param phrases 短语（JSON格式）
 * @param synos 同近义词（JSON格式）
 * @param relWords 相关词（JSON格式）
 * @param etymology 词源（JSON格式）
 * @param primaryPos 主要词性
 * @param primaryDefinition 主要释义
 * @param tags 标签
 * @param sequenceNo 词库内顺序
 * @param difficultyLevel 词库内难度
 * @param examFrequency 考频或权重
 */
@Schema(description = "词库内单词查询行")
public record WordRow(
        Long id,
        String word,
        String normalizedWord,
        String phonetic0,
        String phonetic1,
        String trans,
        String sentences,
        String phrases,
        String synos,
        String relWords,
        String etymology,
        String primaryPos,
        String primaryDefinition,
        String tags,
        Integer sequenceNo,
        Integer difficultyLevel,
        Integer examFrequency
) {
}
