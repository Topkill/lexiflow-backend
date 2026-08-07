package com.lexiflow.wordbook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单词响应 DTO。
 * <p>返回单词的完整信息，包括拼写、音标、释义、例句、短语等。</p>
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
@Schema(description = "单词响应")
public record WordResponse(
        @Schema(description = "单词 ID", example = "1900000000000002001") String id,
        @Schema(description = "单词展示值", example = "ability") String word,
        @Schema(description = "规范化单词", example = "ability") String normalizedWord,
        @Schema(description = "英式音标", example = "əˈbɪləti") String phonetic0,
        @Schema(description = "美式音标", example = "əˈbɪləti") String phonetic1,
        @Schema(description = "释义 JSON") String trans,
        @Schema(description = "例句 JSON") String sentences,
        @Schema(description = "短语 JSON") String phrases,
        @Schema(description = "同近义词 JSON") String synos,
        @Schema(description = "相关词 JSON") String relWords,
        @Schema(description = "词源 JSON") String etymology,
        @Schema(description = "主要词性", example = "n.") String primaryPos,
        @Schema(description = "主释义", example = "能力；才能") String primaryDefinition,
        @Schema(description = "标签") String tags,
        @Schema(description = "词库内顺序", example = "1") Integer sequenceNo,
        @Schema(description = "词库内难度", example = "2") Integer difficultyLevel,
        @Schema(description = "考频或权重", example = "95") Integer examFrequency
) {
    public static WordResponse from(WordRow row) {
        return new WordResponse(
                String.valueOf(row.id()),
                row.word(),
                row.normalizedWord(),
                row.phonetic0(),
                row.phonetic1(),
                row.trans(),
                row.sentences(),
                row.phrases(),
                row.synos(),
                row.relWords(),
                row.etymology(),
                row.primaryPos(),
                row.primaryDefinition(),
                row.tags(),
                row.sequenceNo(),
                row.difficultyLevel(),
                row.examFrequency()
        );
    }
}
