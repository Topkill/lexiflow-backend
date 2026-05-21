package com.lexiflow.study.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpacedRepetitionService {

    private static final BigDecimal DEFAULT_EASINESS_FACTOR = new BigDecimal("2.50");
    private static final BigDecimal MIN_EASINESS_FACTOR = new BigDecimal("1.30");

    private final UserWordStateMapper userWordStateMapper;

    public SpacedRepetitionResult applyFeedback(
            Long userId,
            Long wordbookId,
            Long wordId,
            Long planId,
            StudyFeedback feedback,
            StudyScene scene
    ) {
        UserWordState state = findUserWordState(userId, wordbookId, wordId);
        MasteryStatus oldMasteryStatus = state == null ? MasteryStatus.NEW : state.getMasteryStatus();
        if (state == null) {
            state = newDefaultWordState(userId, wordbookId, wordId, planId);
            applyFeedback(state, feedback, scene);
            userWordStateMapper.insert(state);
        } else {
            if (planId != null) {
                state.setPlanId(planId);
            }
            applyFeedback(state, feedback, scene);
            userWordStateMapper.updateById(state);
        }
        return new SpacedRepetitionResult(
                state.getNextReviewDate(),
                qualityScore(feedback),
                oldMasteryStatus,
                state.getMasteryStatus()
        );
    }

    public int qualityScore(StudyFeedback feedback) {
        return switch (feedback) {
            case UNKNOWN -> 2;
            case KNOWN -> 4;
        };
    }

    private UserWordState findUserWordState(Long userId, Long wordbookId, Long wordId) {
        return userWordStateMapper.selectOne(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getWordbookId, wordbookId)
                .eq(UserWordState::getWordId, wordId)
                .last("LIMIT 1"));
    }

    private UserWordState newDefaultWordState(Long userId, Long wordbookId, Long wordId, Long planId) {
        UserWordState state = new UserWordState();
        state.setUserId(userId);
        state.setWordbookId(wordbookId);
        state.setWordId(wordId);
        state.setPlanId(planId);
        state.setMasteryStatus(MasteryStatus.NEW);
        state.setLearned(false);
        state.setRepetition(0);
        state.setIntervalDays(0);
        state.setEasinessFactor(DEFAULT_EASINESS_FACTOR);
        state.setWrongCount(0);
        state.setCorrectCount(0);
        state.setDeleted(0);
        return state;
    }

    private void applyFeedback(UserWordState state, StudyFeedback feedback, StudyScene scene) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int quality = qualityScore(feedback);
        int nextRepetition;
        int nextInterval;
        BigDecimal nextEf = nextEasinessFactor(state.getEasinessFactor(), quality);

        if (feedback == StudyFeedback.UNKNOWN) {
            nextRepetition = 0;
            nextInterval = 1;
            int nextWrongCount = safeCount(state.getWrongCount()) + 1;
            state.setWrongCount(nextWrongCount);
            state.setMasteryStatus(nextWrongCount >= 3 ? MasteryStatus.DIFFICULT : MasteryStatus.LEARNING);
        } else {
            nextRepetition = safeCount(state.getRepetition()) + 1;
            nextInterval = nextIntervalDays(nextRepetition, safeCount(state.getIntervalDays()), nextEf);
            state.setCorrectCount(safeCount(state.getCorrectCount()) + 1);
            state.setMasteryStatus(nextRepetition >= 3 ? MasteryStatus.MASTERED : MasteryStatus.REVIEWING);
        }

        state.setLearned(true);
        state.setRepetition(nextRepetition);
        state.setIntervalDays(nextInterval);
        state.setEasinessFactor(nextEf);
        state.setNextReviewDate(today.plusDays(nextInterval));
        state.setLastFeedback(feedback);
        state.setLastStudiedAt(now);
        if (scene == StudyScene.REVIEW) {
            state.setLastReviewedAt(now);
        }
    }

    private int nextIntervalDays(int repetition, int previousInterval, BigDecimal ef) {
        if (repetition <= 1) {
            return 1;
        }
        if (repetition == 2) {
            return 3;
        }
        return Math.max(1, BigDecimal.valueOf(Math.max(previousInterval, 3))
                .multiply(ef)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue());
    }

    private BigDecimal nextEasinessFactor(BigDecimal currentEf, int quality) {
        BigDecimal ef = currentEf == null ? DEFAULT_EASINESS_FACTOR : currentEf;
        BigDecimal q = BigDecimal.valueOf(quality);
        BigDecimal delta = new BigDecimal("0.10")
                .subtract(new BigDecimal("5").subtract(q).multiply(new BigDecimal("0.08")))
                .subtract(new BigDecimal("5").subtract(q).multiply(new BigDecimal("5").subtract(q)).multiply(new BigDecimal("0.02")));
        BigDecimal next = ef.add(delta).setScale(2, RoundingMode.HALF_UP);
        return next.max(MIN_EASINESS_FACTOR);
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    public record SpacedRepetitionResult(
            LocalDate nextReviewDate,
            int qualityScore,
            MasteryStatus oldMasteryStatus,
            MasteryStatus newMasteryStatus
    ) {
    }
}
