package com.lexiflow.admin.wordbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.admin.wordbook.dto.AdminWordQueryRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordResponse;
import com.lexiflow.admin.wordbook.dto.AdminWordbookQueryRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordbookRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordbookResponse;
import com.lexiflow.wordbook.dto.WordbookWordAdminRow;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.domain.WordbookWord;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import com.lexiflow.wordbook.mapper.WordbookWordMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminWordbookService {

    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final WordbookWordMapper wordbookWordMapper;

    public PageResponse<AdminWordbookResponse> pageWordbooks(AdminWordbookQueryRequest request) {
        AdminWordbookQueryRequest safeRequest = request == null ? new AdminWordbookQueryRequest(null, null, null, null, null) : request;
        LambdaQueryWrapper<Wordbook> wrapper = new LambdaQueryWrapper<Wordbook>()
                .orderByAsc(Wordbook::getSortOrder)
                .orderByDesc(Wordbook::getCreatedAt)
                .orderByDesc(Wordbook::getId);
        if (safeRequest.type() != null) {
            wrapper.eq(Wordbook::getType, safeRequest.type());
        }
        if (safeRequest.enabled() != null) {
            wrapper.eq(Wordbook::getEnabled, safeRequest.enabled());
        }
        if (StringUtils.hasText(safeRequest.keyword())) {
            String keyword = safeRequest.keyword().trim();
            wrapper.and(query -> query.like(Wordbook::getName, keyword).or().like(Wordbook::getCode, keyword));
        }
        Page<Wordbook> page = wordbookMapper.selectPage(Page.of(safeRequest.safePage(), safeRequest.safeSize()), wrapper);
        return PageResponse.of(
                page.getRecords().stream().map(AdminWordbookResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    public AdminWordbookResponse getWordbook(Long wordbookId) {
        return AdminWordbookResponse.from(getWordbookEntity(wordbookId));
    }

    @Transactional
    public AdminWordbookResponse createWordbook(Long adminUserId, AdminWordbookRequest request) {
        String code = normalizeCode(request.code());
        ensureWordbookCodeAvailable(code, null);
        Wordbook wordbook = new Wordbook();
        applyWordbookRequest(wordbook, request, code);
        wordbook.setWordCount(0);
        wordbook.setCreatedBy(adminUserId);
        wordbook.setUpdatedBy(adminUserId);
        wordbook.setDeleted(0);
        wordbook.setVersion(0);
        wordbookMapper.insert(wordbook);
        return AdminWordbookResponse.from(wordbook);
    }

    @Transactional
    public AdminWordbookResponse updateWordbook(Long adminUserId, Long wordbookId, AdminWordbookRequest request) {
        Wordbook wordbook = getWordbookEntity(wordbookId);
        String code = normalizeCode(request.code());
        ensureWordbookCodeAvailable(code, wordbookId);
        applyWordbookRequest(wordbook, request, code);
        wordbook.setUpdatedBy(adminUserId);
        wordbookMapper.updateById(wordbook);
        return AdminWordbookResponse.from(wordbook);
    }

    @Transactional
    public void enableWordbook(Long adminUserId, Long wordbookId) {
        Wordbook wordbook = getWordbookEntity(wordbookId);
        wordbook.setEnabled(true);
        wordbook.setUpdatedBy(adminUserId);
        wordbookMapper.updateById(wordbook);
    }

    @Transactional
    public void disableWordbook(Long adminUserId, Long wordbookId) {
        Wordbook wordbook = getWordbookEntity(wordbookId);
        wordbook.setEnabled(false);
        wordbook.setUpdatedBy(adminUserId);
        wordbookMapper.updateById(wordbook);
    }

    public PageResponse<AdminWordResponse> pageWords(Long wordbookId, AdminWordQueryRequest request) {
        getWordbookEntity(wordbookId);
        AdminWordQueryRequest safeRequest = request == null ? new AdminWordQueryRequest(null, null, null, null) : request;
        String keyword = StringUtils.hasText(safeRequest.keyword()) ? safeRequest.keyword().trim() : null;
        Page<WordbookWordAdminRow> page = Page.of(safeRequest.safePage(), safeRequest.safeSize());
        var result = wordbookWordMapper.selectAdminWordPage(page, wordbookId, keyword, safeRequest.enabled());
        return PageResponse.of(
                result.getRecords().stream().map(this::toWordResponse).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        );
    }

    @Transactional
    public AdminWordResponse createWord(Long adminUserId, Long wordbookId, AdminWordRequest request) {
        getWordbookEntity(wordbookId);
        Word word = findOrCreateWord(adminUserId, request);
        if (findRelation(wordbookId, word.getId()) != null) {
            throw new BizException(ErrorCode.CONFLICT, "单词已存在于当前词库");
        }
        ensureSequenceAvailable(wordbookId, request.sequenceNo(), null);
        WordbookWord relation = new WordbookWord();
        relation.setWordbookId(wordbookId);
        relation.setWordId(word.getId());
        applyRelationRequest(relation, request);
        relation.setDeleted(0);
        relation.setVersion(0);
        wordbookWordMapper.insert(relation);
        refreshWordbookCount(wordbookId);
        return getWordResponse(wordbookId, word.getId());
    }

    @Transactional
    public AdminWordResponse updateWord(Long adminUserId, Long wordbookId, Long wordId, AdminWordRequest request) {
        getWordbookEntity(wordbookId);
        WordbookWord relation = requireRelation(wordbookId, wordId);
        Word word = requireWord(wordId);
        String normalizedWordText = normalizeWordText(request.wordText());
        ensureWordTextAvailable(normalizedWordText, wordId);
        applyWordRequest(word, request, normalizedWordText, adminUserId);
        wordMapper.updateById(word);
        ensureSequenceAvailable(wordbookId, request.sequenceNo(), relation.getId());
        applyRelationRequest(relation, request);
        wordbookWordMapper.updateById(relation);
        refreshWordbookCount(wordbookId);
        return getWordResponse(wordbookId, wordId);
    }

    @Transactional
    public void removeWord(Long wordbookId, Long wordId) {
        getWordbookEntity(wordbookId);
        WordbookWord relation = requireRelation(wordbookId, wordId);
        wordbookWordMapper.deleteById(relation.getId());
        refreshWordbookCount(wordbookId);
    }

    private Word findOrCreateWord(Long adminUserId, AdminWordRequest request) {
        String normalizedWordText = normalizeWordText(request.wordText());
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getWordText, normalizedWordText)
                .last("LIMIT 1"));
        if (word == null) {
            word = new Word();
            applyWordRequest(word, request, normalizedWordText, adminUserId);
            word.setCreatedBy(adminUserId);
            word.setDeleted(0);
            word.setVersion(0);
            wordMapper.insert(word);
            return word;
        }
        applyWordRequest(word, request, normalizedWordText, adminUserId);
        wordMapper.updateById(word);
        return word;
    }

    private AdminWordResponse getWordResponse(Long wordbookId, Long wordId) {
        WordbookWordAdminRow row = wordbookWordMapper.selectAdminWord(wordbookId, wordId);
        if (row == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return toWordResponse(row);
    }

    private AdminWordResponse toWordResponse(WordbookWordAdminRow row) {
        return new AdminWordResponse(
                String.valueOf(row.id()),
                String.valueOf(row.relationId()),
                row.wordText(),
                row.displayText(),
                row.phoneticUs(),
                row.phoneticUk(),
                row.primaryPos(),
                row.primaryDefinition(),
                row.exampleSentence(),
                row.exampleTranslation(),
                row.tags(),
                row.sequenceNo(),
                row.difficultyLevel(),
                row.examFrequency(),
                row.enabled()
        );
    }
    private void applyWordbookRequest(Wordbook wordbook, AdminWordbookRequest request, String code) {
        wordbook.setName(request.name().trim());
        wordbook.setCode(code);
        wordbook.setType(request.type());
        wordbook.setDescription(trimToNull(request.description()));
        wordbook.setCoverUrl(trimToNull(request.coverUrl()));
        wordbook.setDifficultyLevel(request.difficultyLevel());
        wordbook.setEnabled(request.enabled());
        wordbook.setSortOrder(request.sortOrder());
    }

    private void applyWordRequest(Word word, AdminWordRequest request, String normalizedWordText, Long adminUserId) {
        word.setWordText(normalizedWordText);
        word.setDisplayText(StringUtils.hasText(request.displayText()) ? request.displayText().trim() : request.wordText().trim());
        word.setPhoneticUs(trimToNull(request.phoneticUs()));
        word.setPhoneticUk(trimToNull(request.phoneticUk()));
        word.setMeanings(request.meanings().trim());
        word.setPrimaryPos(trimToNull(request.primaryPos()));
        word.setPrimaryDefinition(trimToNull(request.primaryDefinition()));
        word.setExampleSentence(trimToNull(request.exampleSentence()));
        word.setExampleTranslation(trimToNull(request.exampleTranslation()));
        word.setTags(trimToNull(request.tags()));
        word.setUpdatedBy(adminUserId);
    }

    private void applyRelationRequest(WordbookWord relation, AdminWordRequest request) {
        relation.setSequenceNo(request.sequenceNo());
        relation.setDifficultyLevel(request.difficultyLevel());
        relation.setExamFrequency(request.examFrequency());
        relation.setEnabled(request.enabled());
    }

    private Wordbook getWordbookEntity(Long wordbookId) {
        Wordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null) {
            throw new BizException(ErrorCode.WORDBOOK_NOT_FOUND);
        }
        return wordbook;
    }

    private Word requireWord(Long wordId) {
        Word word = wordMapper.selectById(wordId);
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
    }

    private WordbookWord requireRelation(Long wordbookId, Long wordId) {
        WordbookWord relation = findRelation(wordbookId, wordId);
        if (relation == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return relation;
    }

    private WordbookWord findRelation(Long wordbookId, Long wordId) {
        return wordbookWordMapper.selectOne(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbookId)
                .eq(WordbookWord::getWordId, wordId)
                .last("LIMIT 1"));
    }

    private void ensureWordbookCodeAvailable(String code, Long excludedId) {
        LambdaQueryWrapper<Wordbook> wrapper = new LambdaQueryWrapper<Wordbook>().eq(Wordbook::getCode, code);
        if (excludedId != null) {
            wrapper.ne(Wordbook::getId, excludedId);
        }
        if (wordbookMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "词库编码已存在");
        }
    }

    private void ensureWordTextAvailable(String wordText, Long excludedId) {
        LambdaQueryWrapper<Word> wrapper = new LambdaQueryWrapper<Word>().eq(Word::getWordText, wordText);
        if (excludedId != null) {
            wrapper.ne(Word::getId, excludedId);
        }
        if (wordMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "单词已存在");
        }
    }

    private void ensureSequenceAvailable(Long wordbookId, Integer sequenceNo, Long excludedRelationId) {
        LambdaQueryWrapper<WordbookWord> wrapper = new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbookId)
                .eq(WordbookWord::getSequenceNo, sequenceNo);
        if (excludedRelationId != null) {
            wrapper.ne(WordbookWord::getId, excludedRelationId);
        }
        if (wordbookWordMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "词库内顺序号已存在");
        }
    }

    private void refreshWordbookCount(Long wordbookId) {
        Long count = wordbookWordMapper.selectCount(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbookId)
                .eq(WordbookWord::getEnabled, true));
        Wordbook wordbook = getWordbookEntity(wordbookId);
        wordbook.setWordCount(count.intValue());
        wordbookMapper.updateById(wordbook);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeWordText(String wordText) {
        return wordText.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}