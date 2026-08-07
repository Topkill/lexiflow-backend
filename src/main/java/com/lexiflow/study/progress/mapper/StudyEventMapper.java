package com.lexiflow.study.progress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.dto.StudyEventCorrectWrongCount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学习事件 Mapper 接口。
 * <p>提供学习事件的自定义 SQL 查询，包括活跃用户统计、学习日期查询、正确/错误次数统计等。</p>
 */
public interface StudyEventMapper extends BaseMapper<StudyEvent> {

    /**
     * 统计指定时间范围内的独立活跃用户数。
     *
     * @param start 开始时间（包含）
     * @param end   结束时间（不包含）
     * @return 活跃用户数
     */
    @Select("""
            SELECT COUNT(DISTINCT user_id)
            FROM study_event
            WHERE created_at >= #{start}
              AND created_at < #{end}
            """)
    Long countDistinctUsersBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 查询用户的所有学习活跃日期（去重降序）。
     *
     * @param userId 用户 ID
     * @return 学习活跃日期列表
     */
    @Select("""
            SELECT DISTINCT DATE(created_at)
            FROM study_event
            WHERE user_id = #{userId}
            ORDER BY DATE(created_at) DESC
            """)
    List<LocalDate> selectActiveDates(@Param("userId") Long userId);

    /**
     * 统计指定时间范围内用户的正确/错误事件次数。
     *
     * @param userId     用户 ID
     * @param wordbookId 词书 ID
     * @param start      开始时间（包含）
     * @param end        结束时间（不包含）
     * @return 正确/错误次数统计
     */
    @Select("""
            SELECT
                COALESCE(SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END), 0) AS correctCount,
                COALESCE(SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END), 0) AS wrongCount
            FROM study_event
            WHERE user_id = #{userId}
              AND wordbook_id = #{wordbookId}
              AND created_at >= #{start}
              AND created_at < #{end}
            """)
    StudyEventCorrectWrongCount countCorrectWrongEvents(
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
