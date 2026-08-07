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

/**
 * 间隔重复算法服务。
 * <p>基于 SM-2 算法变体，根据用户的学习反馈动态调整单词的复习间隔和难度因子。
 * 核心逻辑：答对时增加重复次数并延长复习间隔，答错时重置重复次数并缩短间隔。
 * 难度因子会根据回答质量动态调整，最低不低于 1.30。</p>
 */
@Service
@RequiredArgsConstructor
public class SpacedRepetitionService {

    /** 默认难度因子 */
    private static final BigDecimal DEFAULT_EASINESS_FACTOR = new BigDecimal("2.50");
    /** 最低难度因子 */
    private static final BigDecimal MIN_EASINESS_FACTOR = new BigDecimal("1.30");

    private final UserWordStateMapper userWordStateMapper;

    /**
     * 应用学习反馈，更新单词的掌握状态和下次复习日期。
     * <p>若该单词尚无学习状态记录，则创建新记录；否则更新已有记录。</p>
     *
     * @param userId     用户 ID
     * @param wordbookId 词书 ID
     * @param wordId     单词 ID
     * @param planId     学习计划 ID
     * @param feedback   学习反馈（认识/不认识）
     * @param scene      学习场景
     * @return 间隔重复结果，包含下次复习日期和掌握状态变化
     */
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

    /**
     * 将学习反馈转换为质量分数。
     *
     * @param feedback 学习反馈
     * @return 质量分数（UNKNOWN=2, KNOWN=4）
     */
    public int qualityScore(StudyFeedback feedback) {
        return switch (feedback) {
            case UNKNOWN -> 2;
            case KNOWN -> 4;
        };
    }

    /** 查找用户对特定单词的学习状态。 */
    private UserWordState findUserWordState(Long userId, Long wordbookId, Long wordId) {
        return userWordStateMapper.selectOne(new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getWordbookId, wordbookId)
                .eq(UserWordState::getWordId, wordId)
                .last("LIMIT 1"));
    }

    /** 创建默认的单词学习状态。 */
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

    /**
     * 根据反馈更新单词状态的核心逻辑。
     * <p>答错时重置重复次数为 0，间隔设为 1 天；答对时递增重复次数并按算法计算下次间隔。</p>
     */
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

    /**
     * 计算下次复习间隔天数。
     * <p>规则：第 1 次为 1 天，第 2 次为 3 天，之后按 前次间隔 × 难度因子 递增。</p>
     */
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

    /**
     * 计算下一次难度因子。
     * <p>基于 SM-2 公式变体：EF' = EF + 0.1 - (5-q)*0.08 - (5-q)^2*0.02，最低不低于 1.30。</p>
     */
    private BigDecimal nextEasinessFactor(BigDecimal currentEf, int quality) {
        BigDecimal ef = currentEf == null ? DEFAULT_EASINESS_FACTOR : currentEf;
        BigDecimal q = BigDecimal.valueOf(quality);
        BigDecimal delta = new BigDecimal("0.10")
                .subtract(new BigDecimal("5").subtract(q).multiply(new BigDecimal("0.08")))
                .subtract(new BigDecimal("5").subtract(q).multiply(new BigDecimal("5").subtract(q)).multiply(new BigDecimal("0.02")));
        BigDecimal next = ef.add(delta).setScale(2, RoundingMode.HALF_UP);
        return next.max(MIN_EASINESS_FACTOR);
    }

    /** 安全地将可能为 null 的 Integer 转换为 int，null 视为 0。 */
    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 间隔重复算法的计算结果。
     *
     * @param nextReviewDate   下次复习日期
     * @param qualityScore     质量分数
     * @param oldMasteryStatus 反馈前的掌握状态
     * @param newMasteryStatus 反馈后的掌握状态
     */
    public record SpacedRepetitionResult(
            LocalDate nextReviewDate,
            int qualityScore,
            MasteryStatus oldMasteryStatus,
            MasteryStatus newMasteryStatus
    ) {
    }
}
