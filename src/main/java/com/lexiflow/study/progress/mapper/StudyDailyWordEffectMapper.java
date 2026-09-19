package com.lexiflow.study.progress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexiflow.study.progress.domain.StudyDailyWordEffect;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

public interface StudyDailyWordEffectMapper extends BaseMapper<StudyDailyWordEffect> {
    // 同一用户的短学习事务串行化，也保护首次状态创建、跨词库反馈及统计更新。
    @Select("SELECT id FROM users WHERE id = #{userId} FOR UPDATE")
    Long lockUser(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO study_daily_word_effect (user_id, word_id, business_date)
            VALUES (#{userId}, #{wordId}, #{date})
            ON DUPLICATE KEY UPDATE id = id
            """)
    void ensureDay(@Param("userId") Long userId, @Param("wordId") Long wordId, @Param("date") LocalDate date);

    @Select("""
            SELECT * FROM study_daily_word_effect
            WHERE user_id = #{userId} AND word_id = #{wordId} AND business_date = #{date}
            FOR UPDATE
            """)
    StudyDailyWordEffect lockDay(@Param("userId") Long userId, @Param("wordId") Long wordId, @Param("date") LocalDate date);
}
