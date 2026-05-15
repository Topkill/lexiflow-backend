package com.lexiflow.quiz.cloze.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.wordbook.domain.Word;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClozeBlankWordSelectorTest {

    private final ClozeBlankWordSelector selector = new ClozeBlankWordSelector();

    @Test
    void selectBlankWordsShouldPreferUnknownThenVagueThenKnown() {
        List<Word> words = words(1, 2, 3, 4, 5);
        Map<Long, StudyFeedback> feedbackMap = Map.of(
                1L, StudyFeedback.KNOWN,
                2L, StudyFeedback.VAGUE,
                3L, StudyFeedback.UNKNOWN,
                4L, StudyFeedback.KNOWN,
                5L, StudyFeedback.UNKNOWN
        );

        List<Long> selectedIds = selector.selectBlankWords(words, feedbackMap, 4, 100L)
                .stream()
                .map(Word::getId)
                .toList();

        assertThat(selectedIds.subList(0, 2)).containsExactlyInAnyOrder(3L, 5L);
        assertThat(selectedIds.get(2)).isEqualTo(2L);
        assertThat(selectedIds.get(3)).isIn(1L, 4L);
    }

    @Test
    void selectBlankWordsShouldBeStableForSameDailyTask() {
        List<Word> words = words(1, 2, 3, 4, 5, 6, 7, 8);
        Map<Long, StudyFeedback> feedbackMap = Map.of(
                1L, StudyFeedback.UNKNOWN,
                2L, StudyFeedback.UNKNOWN,
                3L, StudyFeedback.UNKNOWN,
                4L, StudyFeedback.VAGUE,
                5L, StudyFeedback.VAGUE,
                6L, StudyFeedback.KNOWN,
                7L, StudyFeedback.KNOWN,
                8L, StudyFeedback.KNOWN
        );

        List<Long> first = selector.selectBlankWords(words, feedbackMap, 5, 200L).stream().map(Word::getId).toList();
        List<Long> second = selector.selectBlankWords(words, feedbackMap, 5, 200L).stream().map(Word::getId).toList();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void higherFeedbackShouldKeepHighestPriorityFeedback() {
        assertThat(ClozeBlankWordSelector.higherFeedback(StudyFeedback.KNOWN, StudyFeedback.UNKNOWN))
                .isEqualTo(StudyFeedback.UNKNOWN);
        assertThat(ClozeBlankWordSelector.higherFeedback(StudyFeedback.VAGUE, StudyFeedback.KNOWN))
                .isEqualTo(StudyFeedback.VAGUE);
    }

    private List<Word> words(long... ids) {
        return java.util.Arrays.stream(ids)
                .mapToObj(this::word)
                .toList();
    }

    private Word word(long id) {
        Word word = new Word();
        word.setId(id);
        word.setDisplayText("word" + id);
        return word;
    }
}
