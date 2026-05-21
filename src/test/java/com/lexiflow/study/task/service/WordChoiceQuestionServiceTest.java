package com.lexiflow.study.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.study.task.dto.ChoiceQuestionResponse;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.mapper.WordMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WordChoiceQuestionServiceTest {

    @Mock
    private WordMapper wordMapper;

    private WordChoiceQuestionService service;

    @BeforeEach
    void setUp() {
        service = new WordChoiceQuestionService(wordMapper, new ObjectMapper());
    }

    @Test
    void buildQuestionShouldReturnFourOptionsAndExcludeInvalidCandidates() {
        Word target = word(1, "conduct", "n.", "行为；实施", "[{\"pos\":\"n.\",\"cn\":\"行为；实施\",\"frequency\":4}]", related("conductive"));
        List<Word> candidates = List.of(
                target,
                word(2, "contract", "n.", "合同；收缩", "[{\"pos\":\"n.\",\"cn\":\"合同；收缩\",\"frequency\":3}]", null),
                word(3, "conflict", "n.", "冲突", "[{\"pos\":\"n.\",\"cn\":\"冲突\",\"frequency\":3}]", null),
                word(4, "conductive", "adj.", "导电的", "[{\"pos\":\"adj.\",\"cn\":\"导电的\",\"frequency\":2}]", null),
                word(5, "conduct", "v.", "引导", "[{\"pos\":\"v.\",\"cn\":\"引导\",\"frequency\":2}]", null),
                word(6, "behavior", "n.", "行为；实施", "[{\"pos\":\"n.\",\"cn\":\"行为；实施\",\"frequency\":2}]", null)
        );
        when(wordMapper.selectChoiceQuestionCandidates(100L)).thenReturn(candidates);

        ChoiceQuestionResponse question = service.buildQuestion(200L, 100L, target);

        assertThat(question.options()).hasSize(4);
        List<String> optionWordIds = question.options().stream().map(option -> option.wordId()).toList();
        assertThat(optionWordIds).contains("1");
        assertThat(optionWordIds).doesNotContain("5", "6");
        assertThat(question.options().get(question.correctIndex()).wordId()).isEqualTo("1");
    }

    @Test
    void buildQuestionShouldFillFromDailyTaskWordsWhenSimilarCandidatesAreNotEnough() {
        Word target = word(1, "abc", "n.", "甲", "[{\"pos\":\"n.\",\"cn\":\"甲\",\"frequency\":4}]", related("abcd"));
        Word similar = word(2, "abcd", "v.", "乙", "[{\"pos\":\"v.\",\"cn\":\"乙\",\"frequency\":3}]", null);
        Word groupDistractorA = word(3, "rst", "adj.", "丙", "[{\"pos\":\"adj.\",\"cn\":\"丙\",\"frequency\":2}]", null);
        Word groupDistractorB = word(4, "uvw", "adv.", "丁", "[{\"pos\":\"adv.\",\"cn\":\"丁\",\"frequency\":2}]", null);
        Word wordbookFallbackA = word(5, "xyz", "prep.", "戊", "[{\"pos\":\"prep.\",\"cn\":\"戊\",\"frequency\":1}]", null);
        Word wordbookFallbackB = word(6, "qop", "conj.", "己", "[{\"pos\":\"conj.\",\"cn\":\"己\",\"frequency\":1}]", null);
        when(wordMapper.selectChoiceQuestionCandidates(100L)).thenReturn(List.of(target, similar, wordbookFallbackA, wordbookFallbackB));

        ChoiceQuestionResponse question = service.buildQuestion(
                200L,
                100L,
                target,
                List.of(target, groupDistractorA, groupDistractorB)
        );
        ChoiceQuestionResponse second = service.buildQuestion(
                200L,
                100L,
                target,
                List.of(target, groupDistractorA, groupDistractorB)
        );

        assertThat(question.options()).hasSize(4);
        List<String> optionWordIds = question.options().stream().map(option -> option.wordId()).toList();
        assertThat(optionWordIds).contains("1", "2", "3", "4");
        assertThat(optionWordIds).doesNotContain("5", "6");
        assertThat(second.options()).isEqualTo(question.options());
        assertThat(second.correctIndex()).isEqualTo(question.correctIndex());
    }

    @Test
    void buildQuestionShouldKeepStableOrderForSameTaskItem() {
        Word target = word(1, "conduct", "n.", "行为；实施", "[{\"pos\":\"n.\",\"cn\":\"行为；实施\",\"frequency\":4}]", related("conductive"));
        List<Word> candidates = List.of(
                target,
                word(2, "contract", "n.", "合同；收缩", "[{\"pos\":\"n.\",\"cn\":\"合同；收缩\",\"frequency\":3}]", null),
                word(3, "conflict", "n.", "冲突", "[{\"pos\":\"n.\",\"cn\":\"冲突\",\"frequency\":3}]", null),
                word(4, "conductive", "adj.", "导电的", "[{\"pos\":\"adj.\",\"cn\":\"导电的\",\"frequency\":2}]", null),
                word(7, "conductor", "n.", "指挥；导体", "[{\"pos\":\"n.\",\"cn\":\"指挥；导体\",\"frequency\":2}]", null)
        );
        when(wordMapper.selectChoiceQuestionCandidates(100L)).thenReturn(candidates);

        ChoiceQuestionResponse first = service.buildQuestion(200L, 100L, target);
        ChoiceQuestionResponse second = service.buildQuestion(200L, 100L, target);

        assertThat(second.options()).isEqualTo(first.options());
        assertThat(second.correctIndex()).isEqualTo(first.correctIndex());
    }

    private Word word(long id, String text, String pos, String definition, String trans, String relWords) {
        Word word = new Word();
        word.setId(id);
        word.setWordbookId(100L);
        word.setWord(text);
        word.setPrimaryPos(pos);
        word.setPrimaryDefinition(definition);
        word.setTrans(trans);
        word.setRelWords(relWords);
        return word;
    }

    private String related(String text) {
        return "{\"root\":\"\",\"rels\":[{\"pos\":\"\",\"words\":[{\"c\":\"" + text + "\",\"cn\":\"\"}]}]}";
    }
}
