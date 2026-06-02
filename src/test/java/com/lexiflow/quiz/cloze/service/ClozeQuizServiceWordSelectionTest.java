package com.lexiflow.quiz.cloze.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.infra.redis.RedisDistributedLockService;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.quiz.cloze.domain.ClozeSourceType;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptAnswerMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizBlankMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.progress.service.SpacedRepetitionService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.mapper.WordMapper;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClozeQuizServiceWordSelectionTest {

    private static final Long USER_ID = 10L;
    private static final Long TASK_ID = 20L;
    private static final Long WORDBOOK_ID = 30L;

    @Mock
    private AsyncTaskService asyncTaskService;
    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private ClozeBlankWordSelector clozeBlankWordSelector;
    @Mock
    private DailyTaskMapper dailyTaskMapper;
    @Mock
    private DailyTaskItemMapper dailyTaskItemMapper;
    @Mock
    private WordMapper wordMapper;
    @Mock
    private ClozeQuizMapper clozeQuizMapper;
    @Mock
    private ClozeQuizBlankMapper clozeQuizBlankMapper;
    @Mock
    private ClozeAttemptMapper clozeAttemptMapper;
    @Mock
    private ClozeAttemptAnswerMapper clozeAttemptAnswerMapper;
    @Mock
    private StudyEventMapper studyEventMapper;
    @Mock
    private WrongWordMapper wrongWordMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SpacedRepetitionService spacedRepetitionService;
    @Mock
    private AiPromptTemplateService aiPromptTemplateService;
    @Mock
    private RedisDistributedLockService redisDistributedLockService;

    @InjectMocks
    private ClozeQuizService service;

    @Test
    void todayNewShouldUseReviewWordsWhenNewWordsAreInsufficient() {
        mockWordLookup();
        when(dailyTaskItemMapper.selectList(any()))
                .thenReturn(taskItems(DailyTaskItemType.NEW, 1, 2))
                .thenReturn(taskItems(DailyTaskItemType.REVIEW, 3, 4, 5, 6, 7));

        Object selection = select(ClozeSourceType.TODAY_NEW, 5);

        List<Long> blankWordIds = wordIds(selection, "blankWords");
        List<Long> backgroundWordIds = wordIds(selection, "backgroundWords");
        assertThat(blankWordIds).hasSize(5).contains(1L, 2L).allSatisfy(id -> assertThat(id).isBetween(1L, 7L));
        assertThat(backgroundWordIds).hasSize(2).doesNotContainAnyElementsOf(blankWordIds);
        assertThat(backgroundWordIds).allSatisfy(id -> assertThat(id).isBetween(3L, 7L));
    }

    @Test
    void wrongWordsShouldOnlyUseUnresolvedWrongWordsAsBlanks() {
        mockWordLookup();
        when(wrongWordMapper.selectList(any())).thenReturn(wrongWords(1, 2, 3, 4, 5, 6));
        when(dailyTaskItemMapper.selectList(any()))
                .thenReturn(taskItems(DailyTaskItemType.NEW, 11, 12))
                .thenReturn(taskItems(DailyTaskItemType.REVIEW, 13));

        Object selection = select(ClozeSourceType.WRONG_WORDS, 5);

        List<Long> blankWordIds = wordIds(selection, "blankWords");
        List<Long> backgroundWordIds = wordIds(selection, "backgroundWords");
        assertThat(blankWordIds).hasSize(5).allSatisfy(id -> assertThat(id).isBetween(1L, 6L));
        assertThat(backgroundWordIds).doesNotContainAnyElementsOf(blankWordIds);
        assertThat(backgroundWordIds).allSatisfy(id -> assertThat(id).isIn(1L, 2L, 3L, 4L, 5L, 6L, 11L, 12L, 13L));
    }

    @Test
    void wrongWordsShouldFailWhenWrongWordsAreInsufficient() {
        when(wrongWordMapper.selectList(any())).thenReturn(wrongWords(1, 2, 3, 4));

        assertThatThrownBy(() -> select(ClozeSourceType.WRONG_WORDS, 5))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未解决错词不足");
    }

    @Test
    void mixedShouldUseSixtyFortyQuotaWhenBothPoolsAreEnough() {
        mockWordLookup();
        when(dailyTaskItemMapper.selectList(any()))
                .thenReturn(taskItems(DailyTaskItemType.NEW, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
                .thenReturn(taskItems(DailyTaskItemType.REVIEW, 201, 202));
        when(wrongWordMapper.selectList(any())).thenReturn(wrongWords(101, 102, 103, 104, 105, 106, 107, 108, 109, 110));

        Object selection = select(ClozeSourceType.MIXED, 10);

        List<Long> blankWordIds = wordIds(selection, "blankWords");
        assertThat(blankWordIds).hasSize(10);
        assertThat(blankWordIds.stream().filter(id -> id >= 1 && id <= 10).count()).isEqualTo(6);
        assertThat(blankWordIds.stream().filter(id -> id >= 101 && id <= 110).count()).isEqualTo(4);
    }

    @Test
    void mixedShouldLetWrongWordsFillWhenNewWordsAreInsufficient() {
        mockWordLookup();
        when(dailyTaskItemMapper.selectList(any()))
                .thenReturn(taskItems(DailyTaskItemType.NEW, 1, 2))
                .thenReturn(taskItems(DailyTaskItemType.REVIEW, 201));
        when(wrongWordMapper.selectList(any())).thenReturn(wrongWords(101, 102, 103, 104, 105));

        Object selection = select(ClozeSourceType.MIXED, 5);

        List<Long> blankWordIds = wordIds(selection, "blankWords");
        assertThat(blankWordIds).hasSize(5);
        assertThat(blankWordIds.stream().filter(id -> id >= 1 && id <= 2).count()).isEqualTo(2);
        assertThat(blankWordIds.stream().filter(id -> id >= 101 && id <= 105).count()).isEqualTo(3);
    }

    @Test
    void mixedShouldFailWhenNewAndWrongWordsAreInsufficient() {
        when(dailyTaskItemMapper.selectList(any()))
                .thenReturn(taskItems(DailyTaskItemType.NEW, 1, 2))
                .thenReturn(taskItems(DailyTaskItemType.REVIEW, 201));
        when(wrongWordMapper.selectList(any())).thenReturn(wrongWords(101, 102));

        assertThatThrownBy(() -> select(ClozeSourceType.MIXED, 5))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("今日新词和错词不足");
    }

    @Test
    void programmaticDraftShouldUseMatchedSurfaceFormAsAnswer() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        Word word = word(1, "resemble");
        JsonNode content = new ObjectMapper().readTree("""
                {
                  "passage": "The new situation resembles an old problem.",
                  "explanations": [
                    {
                      "word": "resemble",
                      "usedForm": "resembles",
                      "usedPos": "v.",
                      "definitionZh": "像",
                      "reasonZh": "这里表示情况像旧问题。"
                    }
                  ]
                }
                """);

        Object draft = ReflectionTestUtils.invokeMethod(service, "buildProgrammaticClozeDraft", content, clozeSelection(word));

        String maskedPassage = recordValue(draft, "passage");
        assertThat(maskedPassage).isEqualTo("The new situation ___1___ an old problem.");
        List<?> blanks = recordValue(draft, "blanks");
        assertThat(blanks).hasSize(1);
        assertThat(((com.lexiflow.quiz.cloze.domain.ClozeQuizBlank) blanks.get(0)).getAnswerWord()).isEqualTo("resembles");
    }

    @Test
    void programmaticDraftShouldBlankFirstOccurrenceWhenUsedFormRepeats() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        Word word = word(1, "headline");
        JsonNode content = new ObjectMapper().readTree("""
                {
                  "passage": "Newspaper **headlines** often amplify negativity. Yet the stories behind these headlines reveal deeper layers.",
                  "explanations": [
                    {
                      "word": "headline",
                      "usedForm": "headlines",
                      "usedPos": "n.",
                      "definitionZh": "头条新闻",
                      "reasonZh": "这里表示报纸头条。"
                    }
                  ]
                }
                """);

        Object draft = ReflectionTestUtils.invokeMethod(service, "buildProgrammaticClozeDraft", content, clozeSelection(word));

        String maskedPassage = recordValue(draft, "passage");
        assertThat(maskedPassage).isEqualTo("Newspaper **___1___** often amplify negativity. Yet the stories behind these headlines reveal deeper layers.");
        List<?> blanks = recordValue(draft, "blanks");
        assertThat(blanks).hasSize(1);
        assertThat(((com.lexiflow.quiz.cloze.domain.ClozeQuizBlank) blanks.get(0)).getAnswerWord()).isEqualTo("headlines");
    }

    private void mockWordLookup() {
        when(wordMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            Collection<?> wordIds = invocation.getArgument(0);
            return wordIds.stream()
                    .map(id -> word(Long.parseLong(String.valueOf(id))))
                    .toList();
        });
    }

    private Object select(ClozeSourceType sourceType, int targetWordCount) {
        return ReflectionTestUtils.invokeMethod(
                service,
                "selectClozeWords",
                USER_ID,
                dailyTask(),
                WORDBOOK_ID,
                sourceType,
                targetWordCount
        );
    }

    private Object clozeSelection(Word... words) {
        try {
            Class<?> selectionClass = Class.forName("com.lexiflow.quiz.cloze.service.ClozeQuizService$ClozeWordSelection");
            java.lang.reflect.Constructor<?> constructor = selectionClass.getDeclaredConstructor(List.class, List.class);
            constructor.setAccessible(true);
            List<Word> selectedWords = List.of(words);
            return constructor.newInstance(selectedWords, selectedWords);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> wordIds(Object selection, String accessor) {
        try {
            Method method = selection.getClass().getDeclaredMethod(accessor);
            method.setAccessible(true);
            return ((List<Word>) method.invoke(selection)).stream().map(Word::getId).toList();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T recordValue(Object record, String accessor) {
        try {
            Method method = record.getClass().getDeclaredMethod(accessor);
            method.setAccessible(true);
            return (T) method.invoke(record);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DailyTask dailyTask() {
        DailyTask task = new DailyTask();
        task.setId(TASK_ID);
        return task;
    }

    private List<DailyTaskItem> taskItems(DailyTaskItemType itemType, long... wordIds) {
        return java.util.Arrays.stream(wordIds)
                .mapToObj(wordId -> {
                    DailyTaskItem item = new DailyTaskItem();
                    item.setDailyTaskId(TASK_ID);
                    item.setUserId(USER_ID);
                    item.setWordbookId(WORDBOOK_ID);
                    item.setWordId(wordId);
                    item.setItemType(itemType);
                    return item;
                })
                .toList();
    }

    private List<WrongWord> wrongWords(long... wordIds) {
        return java.util.Arrays.stream(wordIds)
                .mapToObj(wordId -> {
                    WrongWord wrongWord = new WrongWord();
                    wrongWord.setUserId(USER_ID);
                    wrongWord.setWordbookId(WORDBOOK_ID);
                    wrongWord.setWordId(wordId);
                    wrongWord.setResolved(false);
                    return wrongWord;
                })
                .toList();
    }

    private Word word(long id) {
        return word(id, "word" + id);
    }

    private Word word(long id, String text) {
        Word word = new Word();
        word.setId(id);
        word.setWord(text);
        return word;
    }
}
