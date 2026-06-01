package com.lexiflow.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.content.domain.WordAiQa;
import com.lexiflow.ai.content.mapper.WordAiQaMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.note.domain.StudyNote;
import com.lexiflow.note.domain.StudyNoteSourceType;
import com.lexiflow.note.dto.StudyNoteRequest;
import com.lexiflow.note.dto.StudyNoteResponse;
import com.lexiflow.note.mapper.StudyNoteMapper;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAiReview;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptAiReviewMapper;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyNoteServiceTest {

    @Mock
    private StudyNoteMapper studyNoteMapper;
    @Mock
    private WordAiQaMapper wordAiQaMapper;
    @Mock
    private ClozeAttemptAiReviewMapper clozeAttemptAiReviewMapper;
    @Mock
    private WordbookService wordbookService;
    @Mock
    private WordMapper wordMapper;
    @InjectMocks
    private StudyNoteService service;

    @Test
    void createNormalNoteShouldSaveContentOnlyNote() {
        when(studyNoteMapper.insert(any(StudyNote.class))).thenAnswer(invocation -> {
            StudyNote note = invocation.getArgument(0);
            note.setId(11L);
            return 1;
        });

        StudyNoteResponse response = service.createNote(7L, new StudyNoteRequest(
                StudyNoteSourceType.NORMAL,
                null,
                null,
                null,
                "复盘",
                null,
                "今天复习 namely 的用法"
        ));

        ArgumentCaptor<StudyNote> captor = ArgumentCaptor.forClass(StudyNote.class);
        verify(studyNoteMapper).insert(captor.capture());
        StudyNote saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getSourceType()).isEqualTo(StudyNoteSourceType.NORMAL);
        assertThat(saved.getQuotedText()).isNull();
        assertThat(saved.getContentMd()).isEqualTo("今天复习 namely 的用法");
        assertThat(response.noteId()).isEqualTo("11");
        assertThat(response.sourceType()).isEqualTo("NORMAL");
    }

    @Test
    void createNoteShouldRejectEmptyQuoteAndContent() {
        assertThatThrownBy(() -> service.createNote(7L, new StudyNoteRequest(
                StudyNoteSourceType.NORMAL,
                null,
                null,
                null,
                "空笔记",
                " ",
                ""
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少填写一个");
    }

    @Test
    void createWordQaNoteShouldUseSourceWordScope() {
        WordAiQa qa = new WordAiQa();
        qa.setId(21L);
        qa.setWordbookId(3L);
        qa.setWordId(5L);
        when(wordAiQaMapper.selectById(21L)).thenReturn(qa);
        when(studyNoteMapper.insert(any(StudyNote.class))).thenAnswer(invocation -> {
            StudyNote note = invocation.getArgument(0);
            note.setId(22L);
            return 1;
        });

        StudyNoteResponse response = service.createNote(7L, new StudyNoteRequest(
                StudyNoteSourceType.WORD_QA,
                21L,
                3L,
                5L,
                "",
                "AI 回答里的重点",
                ""
        ));

        ArgumentCaptor<StudyNote> captor = ArgumentCaptor.forClass(StudyNote.class);
        verify(studyNoteMapper).insert(captor.capture());
        StudyNote saved = captor.getValue();
        assertThat(saved.getSourceId()).isEqualTo(21L);
        assertThat(saved.getWordbookId()).isEqualTo(3L);
        assertThat(saved.getWordId()).isEqualTo(5L);
        assertThat(response.title()).startsWith("AI 问答摘录");
    }

    @Test
    void createClozeReviewNoteShouldRejectOtherUsersReview() {
        ClozeAttemptAiReview review = new ClozeAttemptAiReview();
        review.setId(31L);
        review.setUserId(8L);
        review.setWordbookId(3L);
        when(clozeAttemptAiReviewMapper.selectById(31L)).thenReturn(review);

        assertThatThrownBy(() -> service.createNote(7L, new StudyNoteRequest(
                StudyNoteSourceType.CLOZE_REVIEW,
                31L,
                3L,
                null,
                "评阅摘录",
                "评阅内容",
                null
        )))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void getNoteShouldIsolateCurrentUser() {
        StudyNote note = new StudyNote();
        note.setId(41L);
        note.setUserId(8L);
        when(studyNoteMapper.selectById(41L)).thenReturn(note);

        assertThatThrownBy(() -> service.getNote(7L, 41L))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.STUDY_NOTE_NOT_FOUND);
    }
}
