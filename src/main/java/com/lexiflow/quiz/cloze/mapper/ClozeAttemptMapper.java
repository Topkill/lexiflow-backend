package com.lexiflow.quiz.cloze.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import com.lexiflow.quiz.cloze.dto.ClozeAccuracyAggregate;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 完形填空作答 Mapper 接口。
 * <p>提供对 {@link ClozeAttempt} 实体的数据库操作，包括正确率统计。</p>
 */
public interface ClozeAttemptMapper extends BaseMapper<ClozeAttempt> {

    @Select("""
            <script>
            SELECT
                COALESCE(SUM(total_blanks), 0) AS totalBlanks,
                COALESCE(SUM(correct_count), 0) AS correctCount
            FROM cloze_attempt
            WHERE user_id = #{userId}
              AND deleted = 0
            <if test="wordbookId != null">
              AND wordbook_id = #{wordbookId}
            </if>
            </script>
            """)
    ClozeAccuracyAggregate sumAccuracy(
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId
    );

    @Select("""
            SELECT
                COALESCE(SUM(total_blanks), 0) AS totalBlanks,
                COALESCE(SUM(correct_count), 0) AS correctCount
            FROM cloze_attempt
            WHERE user_id = #{userId}
              AND wordbook_id = #{wordbookId}
              AND submitted_at >= #{start}
              AND submitted_at < #{end}
              AND deleted = 0
            """)
    ClozeAccuracyAggregate sumAccuracyBetween(
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
