package com.lexiflow.wordbook.dto;

/**
 * 管理员单词行记录 DTO。
 * <p>用于管理员后台查看和编辑单词详情，包含单词的完整信息。</p>
 *
 * @param id 单词ID
 * @param word 单词拼写
 * @param normalizedWord 标准化拼写
 * @param phonetic0 音标0（英式）
 * @param phonetic1 音标1（美式）
 * @param trans 中文释义
 * @param sentences 例句（JSON格式）
 * @param phrases 短语（JSON格式）
 * @param synos 同义词（JSON格式）
 * @param relWords 相关词
 * @param etymology 词根词缀
 * @param primaryPos 主要词性
 * @param primaryDefinition 主要释义
 * @param tags 标签（JSON格式）
 * @param sequenceNo 排序序号
 * @param difficultyLevel 难度等级
 * @param examFrequency 考试频率
 * @param enabled 是否启用
 */
public record AdminWordRow(
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
        Integer examFrequency,
        Boolean enabled
) {
}
