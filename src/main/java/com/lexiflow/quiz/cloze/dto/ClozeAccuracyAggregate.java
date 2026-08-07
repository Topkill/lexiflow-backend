package com.lexiflow.quiz.cloze.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 完形填空正确率聚合 DTO。
 * <p>用于统计完形填空的总空数和正确数。</p>
 */
@Getter
@Setter
public class ClozeAccuracyAggregate {

    /** 总空数 */
    private Long totalBlanks;
    /** 正确数 */
    private Long correctCount;
}
