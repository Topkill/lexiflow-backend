package com.lexiflow.wordbook.importing.domain;

/**
 * 单词导入来源类型枚举。
 * <p>定义导入数据的来源方式。</p>
 */
public enum WordImportSourceType {
    /** Excel 文件导入 */
    EXCEL,
    /** JSON URL 远程导入 */
    JSON_URL
}
