package com.lexiflow.study.progress.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class StudyBusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 业务日偏移小时：凌晨 4:00 才进入新的一天。避免 23:59 与 00:01 被划入两个业务日。 */
    public static final int BUSINESS_DAY_OFFSET_HOURS = 4;

    /** 真实时刻（事件时间戳、学习时间戳用）。 */
    public LocalDateTime now() { return LocalDateTime.now(ZONE); }

    /** 业务日期：凌晨 4 点之前仍算前一天，使深夜学习与次日凌晨归属同一业务日。 */
    public LocalDate businessDate() {
        return businessDateOf(now());
    }

    /** 任意时刻映射到偏移业务日，便于测试。 */
    public static LocalDate businessDateOf(LocalDateTime moment) {
        return moment.minusHours(BUSINESS_DAY_OFFSET_HOURS).toLocalDate();
    }
}