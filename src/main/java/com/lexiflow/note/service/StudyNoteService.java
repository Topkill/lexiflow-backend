package com.lexiflow.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.ai.content.domain.WordAiQa;
import com.lexiflow.ai.content.mapper.WordAiQaMapper;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.note.domain.StudyNote;
import com.lexiflow.note.domain.StudyNoteSourceType;
import com.lexiflow.note.dto.StudyNoteQueryRequest;
import com.lexiflow.note.dto.StudyNoteRequest;
import com.lexiflow.note.dto.StudyNoteResponse;
import com.lexiflow.note.mapper.StudyNoteMapper;
import com.lexiflow.quiz.cloze.domain.ClozeAttemptAiReview;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptAiReviewMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StudyNoteService {

    private static final int TITLE_MAX_LENGTH = 255;

    private final StudyNoteMapper studyNoteMapper;
    private final WordAiQaMapper wordAiQaMapper;
    private final ClozeAttemptAiReviewMapper clozeAttemptAiReviewMapper;
    private final WordbookService wordbookService;
    private final WordMapper wordMapper;

    public PageResponse<StudyNoteResponse> pageNotes(Long userId, StudyNoteQueryRequest request) {
        LambdaQueryWrapper<StudyNote> wrapper = new LambdaQueryWrapper<StudyNote>()
                .eq(StudyNote::getUserId, userId)
                .orderByDesc(StudyNote::getCreatedAt)
                .orderByDesc(StudyNote::getId);
        if (request.sourceType() != null) {
            wrapper.eq(StudyNote::getSourceType, request.sourceType());
        }
        if (request.wordbookId() != null) {
            wrapper.eq(StudyNote::getWordbookId, request.wordbookId());
        }
        if (request.wordId() != null) {
            wrapper.eq(StudyNote::getWordId, request.wordId());
        }
        if (StringUtils.hasText(request.keyword())) {
            String keyword = request.keyword().trim();
            wrapper.and(item -> item
                    .like(StudyNote::getTitle, keyword)
                    .or()
                    .like(StudyNote::getQuotedText, keyword)
                    .or()
                    .like(StudyNote::getContentMd, keyword));
        }
        Page<StudyNote> page = studyNoteMapper.selectPage(Page.of(request.page(), request.size()), wrapper);
        List<StudyNoteResponse> records = page.getRecords().stream()
                .map(StudyNoteResponse::from)
                .toList();
        return PageResponse.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public StudyNoteResponse getNote(Long userId, Long noteId) {
        return StudyNoteResponse.from(getOwnedNote(userId, noteId));
    }

    @Transactional
    public StudyNoteResponse createNote(Long userId, StudyNoteRequest request) {
        NormalizedNote normalized = normalizeAndValidate(userId, request);
        StudyNote note = new StudyNote();
        note.setUserId(userId);
        note.setSourceType(normalized.sourceType());
        note.setSourceId(normalized.sourceId());
        note.setWordbookId(normalized.wordbookId());
        note.setWordId(normalized.wordId());
        note.setTitle(normalized.title());
        note.setQuotedText(normalized.quotedText());
        note.setContentMd(normalized.contentMd());
        note.setDeleted(0);
        studyNoteMapper.insert(note);
        return StudyNoteResponse.from(note);
    }

    @Transactional
    public StudyNoteResponse updateNote(Long userId, Long noteId, StudyNoteRequest request) {
        StudyNote note = getOwnedNote(userId, noteId);
        NormalizedNote normalized = normalizeAndValidate(userId, request);
        note.setSourceType(normalized.sourceType());
        note.setSourceId(normalized.sourceId());
        note.setWordbookId(normalized.wordbookId());
        note.setWordId(normalized.wordId());
        note.setTitle(normalized.title());
        note.setQuotedText(normalized.quotedText());
        note.setContentMd(normalized.contentMd());
        studyNoteMapper.updateById(note);
        return StudyNoteResponse.from(note);
    }

    @Transactional
    public void deleteNote(Long userId, Long noteId) {
        StudyNote note = getOwnedNote(userId, noteId);
        studyNoteMapper.deleteById(note.getId());
    }

    private StudyNote getOwnedNote(Long userId, Long noteId) {
        StudyNote note = studyNoteMapper.selectById(noteId);
        if (note == null || !userId.equals(note.getUserId())) {
            throw new BizException(ErrorCode.STUDY_NOTE_NOT_FOUND);
        }
        return note;
    }

    private NormalizedNote normalizeAndValidate(Long userId, StudyNoteRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        String quotedText = trimToNull(request.quotedText());
        String contentMd = trimToNull(request.contentMd());
        if (!StringUtils.hasText(quotedText) && !StringUtils.hasText(contentMd)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "引用内容和笔记正文至少填写一个");
        }

        StudyNoteSourceType sourceType = request.sourceType() == null ? StudyNoteSourceType.NORMAL : request.sourceType();
        SourceRef sourceRef = validateSource(userId, sourceType, request.sourceId(), request.wordbookId(), request.wordId());
        String title = trimToNull(request.title());
        if (!StringUtils.hasText(title)) {
            title = deriveTitle(sourceType, quotedText, contentMd);
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }
        return new NormalizedNote(sourceType, sourceRef.sourceId(), sourceRef.wordbookId(), sourceRef.wordId(), title, quotedText, contentMd);
    }

    private SourceRef validateSource(Long userId, StudyNoteSourceType sourceType, Long sourceId, Long wordbookId, Long wordId) {
        return switch (sourceType) {
            case NORMAL -> validateNormalSource(sourceId, wordbookId, wordId);
            case WORD_QA -> validateWordQaSource(sourceId, wordbookId, wordId);
            case CLOZE_REVIEW -> validateClozeReviewSource(userId, sourceId, wordbookId);
        };
    }

    private SourceRef validateNormalSource(Long sourceId, Long wordbookId, Long wordId) {
        if (sourceId != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "普通笔记不能关联来源结果 ID");
        }
        if (wordbookId != null) {
            wordbookService.getEnabledWordbook(wordbookId);
        }
        if (wordId != null) {
            if (wordbookId == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "关联单词时必须同时传入词库 ID");
            }
            requireEnabledWord(wordbookId, wordId);
        }
        return new SourceRef(null, wordbookId, wordId);
    }

    private SourceRef validateWordQaSource(Long sourceId, Long wordbookId, Long wordId) {
        if (sourceId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答摘录必须关联问答结果 ID");
        }
        if (wordbookId == null || wordId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答摘录必须同时传入词库 ID 和单词 ID");
        }
        WordAiQa qa = wordAiQaMapper.selectById(sourceId);
        if (qa == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答结果不存在");
        }
        if (!wordbookId.equals(qa.getWordbookId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答来源词库不匹配");
        }
        if (!wordId.equals(qa.getWordId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 问答来源单词不匹配");
        }
        return new SourceRef(sourceId, qa.getWordbookId(), qa.getWordId());
    }

    private SourceRef validateClozeReviewSource(Long userId, Long sourceId, Long wordbookId) {
        if (sourceId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 评阅摘录必须关联评阅结果 ID");
        }
        ClozeAttemptAiReview review = clozeAttemptAiReviewMapper.selectById(sourceId);
        if (review == null || !userId.equals(review.getUserId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 评阅结果不存在");
        }
        if (wordbookId != null && !wordbookId.equals(review.getWordbookId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AI 评阅来源词库不匹配");
        }
        return new SourceRef(sourceId, review.getWordbookId(), null);
    }

    private void requireEnabledWord(Long wordbookId, Long wordId) {
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getId, wordId)
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getEnabled, true)
                .last("LIMIT 1"));
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
    }

    private String deriveTitle(StudyNoteSourceType sourceType, String quotedText, String contentMd) {
        String prefix = switch (sourceType) {
            case NORMAL -> "普通笔记";
            case WORD_QA -> "AI 问答摘录";
            case CLOZE_REVIEW -> "AI 评阅摘录";
        };
        String base = StringUtils.hasText(quotedText) ? quotedText : contentMd;
        String compact = base == null ? "" : base.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(compact)) {
            return prefix;
        }
        return prefix + "：" + (compact.length() > 40 ? compact.substring(0, 40) : compact);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record SourceRef(Long sourceId, Long wordbookId, Long wordId) {
    }

    private record NormalizedNote(
            StudyNoteSourceType sourceType,
            Long sourceId,
            Long wordbookId,
            Long wordId,
            String title,
            String quotedText,
            String contentMd
    ) {
    }
}
