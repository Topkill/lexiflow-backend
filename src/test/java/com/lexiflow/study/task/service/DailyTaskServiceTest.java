package com.lexiflow.study.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.quiz.cloze.mapper.ClozeQuizMapper;
import com.lexiflow.quiz.cloze.service.ClozeQuizService;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.domain.MasteryStatus;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.domain.StudyFeedback;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.FavoriteWordMapper;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.progress.service.SpacedRepetitionService;
import com.lexiflow.study.progress.service.SpacedRepetitionService.SpacedRepetitionResult;
import com.lexiflow.study.service.StudyPlanService;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.domain.DailyTaskItem;
import com.lexiflow.study.task.domain.DailyTaskItemStatus;
import com.lexiflow.study.task.domain.DailyTaskItemType;
import com.lexiflow.study.task.domain.DailyTaskStatus;
import com.lexiflow.study.task.domain.DailyTaskType;
import com.lexiflow.study.task.dto.SubmitFeedbackRequest;
import com.lexiflow.study.task.dto.SubmitFeedbackResponse;
import com.lexiflow.study.task.mapper.DailyTaskItemMapper;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyTaskServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long TASK_ID = 20L;
    private static final Long ITEM_ID = 30L;
    private static final Long PLAN_ID = 40L;
    private static final Long WORDBOOK_ID = 50L;
    private static final Long WORD_ID = 60L;

    @Mock
    private DailyTaskMapper dailyTaskMapper;
    @Mock
    private DailyTaskItemMapper dailyTaskItemMapper;
    @Mock
    private StudyPlanMapper studyPlanMapper;
    @Mock
    private StudyPlanService studyPlanService;
    @Mock
    private WordbookService wordbookService;
    @Mock
    private WordMapper wordMapper;
    @Mock
    private UserWordStateMapper userWordStateMapper;
    @Mock
    private StudyEventMapper studyEventMapper;
    @Mock
    private WrongWordMapper wrongWordMapper;
    @Mock
    private FavoriteWordMapper favoriteWordMapper;
    @Mock
    private ClozeQuizMapper clozeQuizMapper;
    @Mock
    private ClozeAttemptMapper clozeAttemptMapper;
    @Mock
    private ClozeQuizService clozeQuizService;
    @Mock
    private SpacedRepetitionService spacedRepetitionService;
    @Mock
    private WordChoiceQuestionService wordChoiceQuestionService;

    @InjectMocks
    private DailyTaskService dailyTaskService;

    @Test
    void submitKnownFeedbackShouldIncrementDoneCountOnlyAfterClaimingPendingItem() {
        DailyTaskItem item = pendingItem(null);
        DailyTask sourceTask = dailyTask(0, DailyTaskStatus.PENDING);
        DailyTask refreshedTask = dailyTask(1, DailyTaskStatus.DONE);
        StudyPlan plan = studyPlan();
        LocalDate nextReviewDate = LocalDate.now().plusDays(1);

        when(dailyTaskItemMapper.selectOne(any())).thenReturn(item);
        when(dailyTaskMapper.selectOne(any())).thenReturn(sourceTask);
        when(dailyTaskItemMapper.update(any(DailyTaskItem.class), any())).thenReturn(1);
        when(spacedRepetitionService.applyFeedback(USER_ID, WORDBOOK_ID, WORD_ID, PLAN_ID, StudyFeedback.KNOWN, StudyScene.NEW))
                .thenReturn(new SpacedRepetitionResult(nextReviewDate, 4, MasteryStatus.NEW, MasteryStatus.REVIEWING));
        when(studyPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(dailyTaskMapper.update(any(DailyTask.class), any())).thenReturn(1);
        when(dailyTaskMapper.selectById(TASK_ID)).thenReturn(refreshedTask);

        SubmitFeedbackResponse response = dailyTaskService.submitFeedback(
                USER_ID,
                ITEM_ID,
                new SubmitFeedbackRequest(StudyFeedback.KNOWN, 12)
        );

        assertThat(response.status()).isEqualTo(DailyTaskItemStatus.DONE.name());
        assertThat(response.feedback()).isEqualTo(StudyFeedback.KNOWN.name());
        assertThat(response.dailyTaskDone()).isTrue();
        assertThat(response.nextReviewDate()).isEqualTo(nextReviewDate);
        assertThat(response.taskProgress().doneCount()).isEqualTo(1);
        assertThat(response.taskProgress().totalCount()).isEqualTo(1);
        verify(studyEventMapper).insert(any(StudyEvent.class));
        verify(clozeQuizService).prefetchCompletedGroupCloze(USER_ID, TASK_ID);

        ArgumentCaptor<DailyTaskItem> itemUpdateCaptor = ArgumentCaptor.forClass(DailyTaskItem.class);
        verify(dailyTaskItemMapper).update(itemUpdateCaptor.capture(), any());
        assertThat(itemUpdateCaptor.getValue().getStatus()).isEqualTo(DailyTaskItemStatus.DONE);
        assertThat(itemUpdateCaptor.getValue().getFeedback()).isEqualTo(StudyFeedback.KNOWN.name());
    }

    @Test
    void submitKnownFeedbackShouldReuseAlreadyAppliedResultWhenClaimFails() {
        DailyTaskItem pendingSnapshot = pendingItem(null);
        DailyTaskItem completedItem = pendingItem(StudyFeedback.KNOWN.name());
        completedItem.setStatus(DailyTaskItemStatus.DONE);
        DailyTask doneTask = dailyTask(1, DailyTaskStatus.DONE);
        UserWordState state = new UserWordState();
        state.setNextReviewDate(LocalDate.now().plusDays(3));

        when(dailyTaskItemMapper.selectOne(any())).thenReturn(pendingSnapshot, completedItem);
        when(dailyTaskMapper.selectOne(any())).thenReturn(doneTask);
        when(dailyTaskItemMapper.update(any(DailyTaskItem.class), any())).thenReturn(0);
        when(userWordStateMapper.selectOne(any())).thenReturn(state);
        when(dailyTaskMapper.selectById(TASK_ID)).thenReturn(doneTask);

        SubmitFeedbackResponse response = dailyTaskService.submitFeedback(
                USER_ID,
                ITEM_ID,
                new SubmitFeedbackRequest(StudyFeedback.KNOWN, 12)
        );

        assertThat(response.status()).isEqualTo(DailyTaskItemStatus.DONE.name());
        assertThat(response.feedback()).isEqualTo(StudyFeedback.KNOWN.name());
        assertThat(response.taskProgress().doneCount()).isEqualTo(1);
        verify(spacedRepetitionService, never()).applyFeedback(any(), any(), any(), any(), any(), any());
        verify(studyEventMapper, never()).insert(any(StudyEvent.class));
        verify(dailyTaskMapper, never()).update(any(DailyTask.class), any());
    }

    @Test
    void submitUnknownFeedbackShouldReuseAlreadyAppliedResultWhenClaimFails() {
        DailyTaskItem unknownItem = pendingItem(StudyFeedback.UNKNOWN.name());
        DailyTask pendingTask = dailyTask(0, DailyTaskStatus.PENDING);
        UserWordState state = new UserWordState();
        state.setNextReviewDate(LocalDate.now().plusDays(1));

        when(dailyTaskItemMapper.selectOne(any())).thenReturn(unknownItem, unknownItem);
        when(dailyTaskMapper.selectOne(any())).thenReturn(pendingTask);
        when(dailyTaskItemMapper.update(any(DailyTaskItem.class), any())).thenReturn(0);
        when(userWordStateMapper.selectOne(any())).thenReturn(state);
        when(dailyTaskMapper.selectById(TASK_ID)).thenReturn(pendingTask);

        SubmitFeedbackResponse response = dailyTaskService.submitFeedback(
                USER_ID,
                ITEM_ID,
                new SubmitFeedbackRequest(StudyFeedback.UNKNOWN, 12)
        );

        assertThat(response.status()).isEqualTo(DailyTaskItemStatus.PENDING.name());
        assertThat(response.feedback()).isEqualTo(StudyFeedback.UNKNOWN.name());
        assertThat(response.nextReviewDate()).isEqualTo(state.getNextReviewDate());
        assertThat(response.taskProgress().doneCount()).isZero();
        verify(spacedRepetitionService, never()).applyFeedback(any(), any(), any(), any(), any(), any());
        verify(studyEventMapper, never()).insert(any(StudyEvent.class));
        verify(wrongWordMapper, never()).insert(any(WrongWord.class));
        verify(dailyTaskMapper, never()).update(any(DailyTask.class), any());
    }

    private DailyTaskItem pendingItem(String feedback) {
        DailyTaskItem item = new DailyTaskItem();
        item.setId(ITEM_ID);
        item.setDailyTaskId(TASK_ID);
        item.setUserId(USER_ID);
        item.setPlanId(PLAN_ID);
        item.setWordbookId(WORDBOOK_ID);
        item.setWordId(WORD_ID);
        item.setItemType(DailyTaskItemType.NEW);
        item.setStatus(DailyTaskItemStatus.PENDING);
        item.setFeedback(feedback);
        return item;
    }

    private DailyTask dailyTask(int doneCount, DailyTaskStatus status) {
        DailyTask task = new DailyTask();
        task.setId(TASK_ID);
        task.setUserId(USER_ID);
        task.setPlanId(PLAN_ID);
        task.setTaskType(DailyTaskType.DAILY);
        task.setStatus(status);
        task.setNewCount(1);
        task.setReviewCount(0);
        task.setExtraCount(0);
        task.setDoneCount(doneCount);
        return task;
    }

    private StudyPlan studyPlan() {
        StudyPlan plan = new StudyPlan();
        plan.setId(PLAN_ID);
        plan.setReviewedCount(0);
        plan.setMasteredCount(0);
        return plan;
    }
}
