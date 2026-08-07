package com.lexiflow.study.progress.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 学习事件正确/错误统计 DTO。
 * <p>用于 MyBatis 自定义 SQL 查询的结果映射，统计指定时间范围内的正确和错误次数。</p>
 */
@Getter
@Setter
public class StudyEventCorrectWrongCount {

    /** 正确次数 */
    private Long correctCount;
    /** 错误次数 */
    private Long wrongCount;
}
