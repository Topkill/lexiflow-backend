package com.lexiflow.study.progress.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("study_daily_word_effect")
public class StudyDailyWordEffect {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long wordId;
    private LocalDate businessDate;
    private Boolean unknownEfApplied;
    private Boolean knownReviewApplied;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
