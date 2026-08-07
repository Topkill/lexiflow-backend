package com.lexiflow.wordbook.importing.domain;

/**
 * 单词导入重复处理策略枚举。
 * <p>定义导入时遇到重复单词的处理方式。</p>
 */
public enum WordImportDuplicateStrategy {
    /** 跳过重复单词 */
    SKIP,
    /** 覆盖重复单词 */
    OVERWRITE,
    /** 仅填充空字段 */
    FILL_EMPTY
}