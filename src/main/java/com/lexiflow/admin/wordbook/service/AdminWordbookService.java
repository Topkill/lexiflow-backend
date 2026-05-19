package com.lexiflow.admin.wordbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.admin.wordbook.dto.AdminWordQueryRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordResponse;
import com.lexiflow.admin.wordbook.dto.AdminWordbookQueryRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordbookRequest;
import com.lexiflow.admin.wordbook.dto.AdminWordbookResponse;
import com.lexiflow.wordbook.dto.AdminWordRow;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.lexiflow.wordbook.service.WordDictionaryJsonService;

@Service
@RequiredArgsConstructor
public class AdminWordbookService {

    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final WordDictionaryJsonService wordDictionaryJsonService;

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
        Page<AdminWordRow> page = Page.of(safeRequest.safePage(), safeRequest.safeSize());
        var result = wordMapper.selectAdminWordPage(page, wordbookId, keyword, safeRequest.enabled());
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
        String normalizedWord = wordDictionaryJsonService.normalizeWord(request.word());
        ensureNormalizedWordAvailable(wordbookId, normalizedWord, null);
        ensureSequenceAvailable(wordbookId, request.sequenceNo(), null);
        Word word = new Word();
        word.setWordbookId(wordbookId);
        applyWordRequest(word, request, normalizedWord, adminUserId);
        applyWordScopeRequest(word, request);
        word.setCreatedBy(adminUserId);
        word.setDeleted(0);
        wordMapper.insert(word);
        refreshWordbookCount(wordbookId);
        return getWordResponse(wordbookId, word.getId());
    }

    @Transactional
    public AdminWordResponse updateWord(Long adminUserId, Long wordbookId, Long wordId, AdminWordRequest request) {
        getWordbookEntity(wordbookId);
        Word word = requireWord(wordbookId, wordId);
        String normalizedWord = wordDictionaryJsonService.normalizeWord(request.word());
        ensureNormalizedWordAvailable(wordbookId, normalizedWord, wordId);
        ensureSequenceAvailable(wordbookId, request.sequenceNo(), wordId);
        applyWordRequest(word, request, normalizedWord, adminUserId);
        applyWordScopeRequest(word, request);
        wordMapper.updateById(word);
        refreshWordbookCount(wordbookId);
        return getWordResponse(wordbookId, wordId);
    }

    @Transactional
    public void removeWord(Long wordbookId, Long wordId) {
        getWordbookEntity(wordbookId);
        Word word = requireWord(wordbookId, wordId);
        wordMapper.deleteById(word.getId());
        refreshWordbookCount(wordbookId);
    }

    private AdminWordResponse getWordResponse(Long wordbookId, Long wordId) {
        AdminWordRow row = wordMapper.selectAdminWord(wordbookId, wordId);
        if (row == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return toWordResponse(row);
    }

    private AdminWordResponse toWordResponse(AdminWordRow row) {
        return new AdminWordResponse(
                String.valueOf(row.id()),
                row.word(),
                row.normalizedWord(),
                row.phonetic0(),
                row.phonetic1(),
                row.trans(),
                row.sentences(),
                row.phrases(),
                row.synos(),
                row.relWords(),
                row.etymology(),
                row.primaryPos(),
                row.primaryDefinition(),
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

    private void applyWordRequest(Word word, AdminWordRequest request, String normalizedWord, Long adminUserId) {
        String trans = wordDictionaryJsonService.normalizeRequiredJson(request.trans(), "释义 JSON");
        WordDictionaryJsonService.WordSummary summary = wordDictionaryJsonService.deriveSummary(trans, request.primaryPos(), request.primaryDefinition());
        word.setWord(request.word().trim());
        word.setNormalizedWord(normalizedWord);
        word.setPhonetic0(trimToNull(request.phonetic0()));
        word.setPhonetic1(trimToNull(request.phonetic1()));
        word.setTrans(trans);
        word.setSentences(wordDictionaryJsonService.normalizeOptionalJson(request.sentences(), "例句 JSON"));
        word.setPhrases(wordDictionaryJsonService.normalizeOptionalJson(request.phrases(), "短语 JSON"));
        word.setSynos(wordDictionaryJsonService.normalizeOptionalJson(request.synos(), "同近义词 JSON"));
        word.setRelWords(wordDictionaryJsonService.normalizeOptionalJson(request.relWords(), "相关词 JSON"));
        word.setEtymology(wordDictionaryJsonService.normalizeOptionalJson(request.etymology(), "词源 JSON"));
        word.setPrimaryPos(summary.primaryPos());
        word.setPrimaryDefinition(summary.primaryDefinition());
        word.setTags(trimToNull(request.tags()));
        word.setUpdatedBy(adminUserId);
    }

    private void applyWordScopeRequest(Word word, AdminWordRequest request) {
        word.setSequenceNo(request.sequenceNo());
        word.setDifficultyLevel(request.difficultyLevel());
        word.setExamFrequency(request.examFrequency());
        word.setEnabled(request.enabled());
    }

    private Wordbook getWordbookEntity(Long wordbookId) {
        Wordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null) {
            throw new BizException(ErrorCode.WORDBOOK_NOT_FOUND);
        }
        return wordbook;
    }

    private Word requireWord(Long wordbookId, Long wordId) {
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getId, wordId)
                .eq(Word::getWordbookId, wordbookId)
                .last("LIMIT 1"));
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
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

    private void ensureNormalizedWordAvailable(Long wordbookId, String normalizedWord, Long excludedId) {
        LambdaQueryWrapper<Word> wrapper = new LambdaQueryWrapper<Word>()
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getNormalizedWord, normalizedWord);
        if (excludedId != null) {
            wrapper.ne(Word::getId, excludedId);
        }
        if (wordMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "当前词库内单词已存在");
        }
    }

    private void ensureSequenceAvailable(Long wordbookId, Integer sequenceNo, Long excludedWordId) {
        LambdaQueryWrapper<Word> wrapper = new LambdaQueryWrapper<Word>()
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getSequenceNo, sequenceNo);
        if (excludedWordId != null) {
            wrapper.ne(Word::getId, excludedWordId);
        }
        if (wordMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "词库内顺序号已存在");
        }
    }

    private void refreshWordbookCount(Long wordbookId) {
        Long count = wordMapper.selectCount(new LambdaQueryWrapper<Word>()
                .eq(Word::getWordbookId, wordbookId)
                .eq(Word::getEnabled, true));
        Wordbook wordbook = getWordbookEntity(wordbookId);
        wordbook.setWordCount(count.intValue());
        wordbookMapper.updateById(wordbook);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
