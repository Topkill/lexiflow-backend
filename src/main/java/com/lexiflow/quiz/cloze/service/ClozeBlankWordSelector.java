package com.lexiflow.quiz.cloze.service;

import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.wordbook.domain.Word;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 完形填空挖空单词选择器。
 * <p>根据学习反馈优先级（未知词优先、已知词其次）从目标词列表中随机选取挖空单词，
 * 确保挖空词分布合理且优先覆盖薄弱词汇。</p>
 */
@Component
public class ClozeBlankWordSelector {

    /**
     * 根据学习反馈优先级选取挖空单词。
     * <p>优先选取未知词（UNKNOWN），不足时补充已知词（KNOWN）。
     * 同一优先级内的单词顺序基于 dailyTaskId 的哈希值随机打乱，保证同一任务内结果稳定。</p>
     *
     * @param targetWords 目标词列表
     * @param feedbackMap 单词 ID 到学习反馈的映射
     * @param blankCount  需要选取的挖空数量
     * @param dailyTaskId 每日任务 ID，用于随机种子
     * @return 选取的挖空单词列表
     */
    public List<Word> selectBlankWords(List<Word> targetWords, Map<Long, StudyFeedback> feedbackMap, int blankCount, Long dailyTaskId) {
        List<Word> unknownWords = shuffleByPriority(targetWords, feedbackMap, FeedbackPriority.UNKNOWN, dailyTaskId);
        List<Word> knownWords = shuffleByPriority(targetWords, feedbackMap, FeedbackPriority.KNOWN, dailyTaskId);
        List<Word> selected = new ArrayList<>();
        appendUntilLimit(selected, unknownWords, blankCount);
        appendUntilLimit(selected, knownWords, blankCount);
        return selected;
    }

    /**
     * 返回两个学习反馈中优先级较高的一个。
     *
     * @param left  左侧反馈
     * @param right 右侧反馈
     * @return 优先级较高的反馈
     */
    public static StudyFeedback higherFeedback(StudyFeedback left, StudyFeedback right) {
        return priorityOf(left).weight >= priorityOf(right).weight ? left : right;
    }

    /**
     * 解析反馈字符串为 {@link StudyFeedback} 枚举。
     * 解析失败或为空时默认返回 {@link StudyFeedback#KNOWN}。
     *
     * @param feedback 反馈字符串
     * @return 对应的 StudyFeedback 枚举值
     */
    public static StudyFeedback parseFeedback(String feedback) {
        if (!StringUtils.hasText(feedback)) {
            return StudyFeedback.KNOWN;
        }
        try {
            return StudyFeedback.valueOf(feedback);
        } catch (IllegalArgumentException ex) {
            return StudyFeedback.KNOWN;
        }
    }

    private List<Word> shuffleByPriority(List<Word> targetWords, Map<Long, StudyFeedback> feedbackMap, FeedbackPriority priority, Long dailyTaskId) {
        List<Word> words = targetWords.stream()
                .filter(word -> priorityOf(feedbackMap.get(word.getId())) == priority)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(words, new Random(Objects.hash(dailyTaskId, priority.name())));
        return words;
    }

    private void appendUntilLimit(List<Word> selected, List<Word> candidates, int limit) {
        for (Word candidate : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            selected.add(candidate);
        }
    }

    private static FeedbackPriority priorityOf(StudyFeedback feedback) {
        if (feedback == null) {
            return FeedbackPriority.KNOWN;
        }
        return switch (feedback) {
            case UNKNOWN -> FeedbackPriority.UNKNOWN;
            case KNOWN -> FeedbackPriority.KNOWN;
        };
    }

    private enum FeedbackPriority {
        UNKNOWN(2),
        KNOWN(1);

        private final int weight;

        FeedbackPriority(int weight) {
            this.weight = weight;
        }
    }
}
