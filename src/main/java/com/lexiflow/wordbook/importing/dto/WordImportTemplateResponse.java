package com.lexiflow.wordbook.importing.dto;

/**
 * 单词导入模板响应 DTO。
 * <p>用于下载 Excel 导入模板，包含文件名和内容。</p>
 *
 * @param fileName 文件名
 * @param content 文件内容（字节数组）
 */
public record WordImportTemplateResponse(
        String fileName,
        byte[] content
) {
}