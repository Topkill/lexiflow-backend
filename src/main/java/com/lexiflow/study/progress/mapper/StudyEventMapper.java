package com.lexiflow.study.progress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.dto.StudyEventCorrectWrongCount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface StudyEventMapper extends BaseMapper<StudyEvent> {

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

    @Select("""
            SELECT DISTINCT DATE(created_at)
            FROM study_event
            WHERE user_id = #{userId}
            ORDER BY DATE(created_at) DESC
            """)
    List<LocalDate> selectActiveDates(@Param("userId") Long userId);

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
