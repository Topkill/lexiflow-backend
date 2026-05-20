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

@Component
public class ClozeBlankWordSelector {

    public List<Word> selectBlankWords(List<Word> targetWords, Map<Long, StudyFeedback> feedbackMap, int blankCount, Long dailyTaskId) {
        List<Word> unknownWords = shuffleByPriority(targetWords, feedbackMap, FeedbackPriority.UNKNOWN, dailyTaskId);
        List<Word> knownWords = shuffleByPriority(targetWords, feedbackMap, FeedbackPriority.KNOWN, dailyTaskId);
        List<Word> selected = new ArrayList<>();
        appendUntilLimit(selected, unknownWords, blankCount);
        appendUntilLimit(selected, knownWords, blankCount);
        return selected;
    }

    public static StudyFeedback higherFeedback(StudyFeedback left, StudyFeedback right) {
        return priorityOf(left).weight >= priorityOf(right).weight ? left : right;
    }

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
