package com.lexiflow.study.dto;

import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.wordbook.domain.Wordbook;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 学习计划响应。
 *
 * @param id                 计划 ID
 * @param wordbookId         词库 ID
 * @param wordbookName       词库名称
 * @param name               计划名称
 * @param newWordsPerGroup   每组新词数
 * @param reviewWordsPerGroup 每组复习词数
 * @param status             计划状态
 * @param totalWords         总词数
 * @param learnedCount       已学新词数
 * @param masteredCount      已掌握词数
 * @param currentSequenceNo  当前学习位置
 * @param expectedFinishDate 预计完成日期
 */
@Schema(description = "学习计划响应")
public record StudyPlanResponse(
        @Schema(description = "计划 ID", example = "1900000000000004001") String id,
        @Schema(description = "词库 ID", example = "1900000000000001001") String wordbookId,
        @Schema(description = "词库名称", example = "CET4 核心词") String wordbookName,
        @Schema(description = "计划名称", example = "CET4 核心词计划") String name,
        @Schema(description = "每组新词数", example = "20") Integer newWordsPerGroup,
        @Schema(description = "每组复习词数", example = "40") Integer reviewWordsPerGroup,
        @Schema(description = "计划状态", example = "ACTIVE") String status,
        @Schema(description = "总词数", example = "4500") Integer totalWords,
        @Schema(description = "已学新词数", example = "120") Integer learnedCount,
        @Schema(description = "已掌握词数", example = "80") Integer masteredCount,
        @Schema(description = "当前学习位置", example = "120") Integer currentSequenceNo,
        @Schema(description = "预计完成日期", example = "2026-10-10") LocalDate expectedFinishDate
) {
    /**
     * 从计划实体和词书实体构造响应。
     *
     * @param plan     学习计划实体
     * @param wordbook 词书实体
     * @return 响应实例
     */
    public static StudyPlanResponse from(StudyPlan plan, Wordbook wordbook) {
        return new StudyPlanResponse(
                String.valueOf(plan.getId()),
                String.valueOf(plan.getWordbookId()),
                wordbook.getName(),
                plan.getName(),
                plan.getNewWordsPerGroup(),
                plan.getReviewWordsPerGroup(),
                plan.getStatus().name(),
                plan.getTotalWords(),
                plan.getLearnedCount(),
                plan.getMasteredCount(),
                plan.getCurrentSequenceNo(),
                plan.getExpectedFinishDate()
        );
    }
}
