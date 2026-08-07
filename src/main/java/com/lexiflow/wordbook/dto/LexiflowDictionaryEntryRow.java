package com.lexiflow.wordbook.dto;

/**
 * 词典条目行 DTO。
 * <p>用于导入或导出词典数据，包含单词的基本信息和扩展信息。</p>
 *
 * @param id 单词ID
 * @param word 单词拼写
 * @param normalizedWord 标准化拼写
 * @param phoneticsJson 音标（JSON格式）
 * @param primaryPos 主要词性
 * @param primaryDefinition 主要释义
 * @param sensesJson 词义（JSON格式）
 * @param source 数据来源
 */
public record LexiflowDictionaryEntryRow(
        Long id,
        String word,
        String normalizedWord,
        String phoneticsJson,
        String primaryPos,
        String primaryDefinition,
        String sensesJson,
        String source
) {
}
