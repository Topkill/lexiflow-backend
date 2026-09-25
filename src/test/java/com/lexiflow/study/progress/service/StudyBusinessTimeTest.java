package com.lexiflow.study.progress.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务日偏移的纯函数测试（不依赖 Spring / 数据库）。
 * <p>业务日 = 上海时刻减 {@link StudyBusinessTime#BUSINESS_DAY_OFFSET_HOURS} 小时后的日历日。
 * 调度器、任务生成与到期列表都以此为准，因此边界语义必须稳定。</p>
 */
class StudyBusinessTimeTest {

    private static LocalDate businessDay(int year, int month, int day, int hour, int minute) {
        return StudyBusinessTime.businessDateOf(LocalDateTime.of(year, month, day, hour, minute));
    }

    @Test
    void 深夜与次日凌晨归属同一业务日() {
        // 4 点偏移的初衷：23:59 学、00:01 复习不算跨天
        assertEquals(businessDay(2026, 9, 20, 23, 59), businessDay(2026, 9, 21, 0, 1));
        assertEquals(LocalDate.of(2026, 9, 20), businessDay(2026, 9, 21, 0, 1));
    }

    @Test
    void 凌晨四点为业务日分界() {
        assertEquals(LocalDate.of(2026, 9, 20), businessDay(2026, 9, 21, 3, 59), "03:59 仍算前一天");
        assertEquals(LocalDate.of(2026, 9, 21), businessDay(2026, 9, 21, 4, 0), "04:00 进入新一天");
        assertEquals(LocalDate.of(2026, 9, 21), businessDay(2026, 9, 21, 4, 1));
    }

    @Test
    void 凌晨学习与四点半复习属于不同业务日() {
        // 修复 formalAllowed 口径后，02:00 学 / 04:30 复习判为跨业务日，
        // 不会再出现"被选进到期任务却只能按日内补练处理"的无效复习
        LocalDate learned = businessDay(2026, 9, 21, 2, 0);
        LocalDate reviewed = businessDay(2026, 9, 21, 4, 30);
        assertEquals(LocalDate.of(2026, 9, 20), learned);
        assertEquals(LocalDate.of(2026, 9, 21), reviewed);
        assertTrue(learned.isBefore(reviewed), "应判定为跨业务日");
    }

    @Test
    void 凌晨三点前学习与复习属于同一业务日() {
        // 03:00 学 / 03:50 复习仍属同一业务日，不应误判为跨日
        assertFalse(businessDay(2026, 9, 21, 3, 0).isBefore(businessDay(2026, 9, 21, 3, 50)));
    }

    @Test
    void 白天时刻业务日与自然日一致() {
        assertEquals(LocalDate.of(2026, 9, 21), businessDay(2026, 9, 21, 12, 0));
        assertEquals(LocalDate.of(2026, 9, 21), businessDay(2026, 9, 21, 23, 59));
    }

    @Test
    void 跨月跨年时偏移正确回退() {
        assertEquals(LocalDate.of(2025, 12, 31), businessDay(2026, 1, 1, 0, 30));
        assertEquals(LocalDate.of(2026, 8, 31), businessDay(2026, 9, 1, 2, 0));
    }
}
