package com.lexiflow.wordbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.dto.WordQueryRequest;
import com.lexiflow.wordbook.dto.WordResponse;
import com.lexiflow.wordbook.dto.WordRow;
import com.lexiflow.wordbook.dto.WordbookQueryRequest;
import com.lexiflow.wordbook.dto.WordbookResponse;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WordbookService {

    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;

    public List<WordbookResponse> listWordbooks(WordbookQueryRequest request) {
        LambdaQueryWrapper<Wordbook> wrapper = new LambdaQueryWrapper<Wordbook>()
                .eq(Wordbook::getEnabled, true)
                .orderByAsc(Wordbook::getSortOrder)
                .orderByAsc(Wordbook::getId);
        if (request != null && request.type() != null) {
            wrapper.eq(Wordbook::getType, request.type());
        }
        if (request != null && StringUtils.hasText(request.keyword())) {
            wrapper.like(Wordbook::getName, request.keyword().trim());
        }
        return wordbookMapper.selectList(wrapper).stream()
                .map(wordbook -> WordbookResponse.from(wordbook, null))
                .toList();
    }

    public WordbookResponse getWordbook(Long wordbookId) {
        return WordbookResponse.from(getEnabledWordbook(wordbookId), null);
    }

    public PageResponse<WordResponse> pageWords(Long wordbookId, WordQueryRequest request) {
        getEnabledWordbook(wordbookId);
        WordQueryRequest safeRequest = request == null ? new WordQueryRequest(null, null, null) : request;
        Page<WordRow> page = Page.of(safeRequest.safePage(), safeRequest.safeSize());
        String keyword = StringUtils.hasText(safeRequest.keyword()) ? safeRequest.keyword().trim() : null;
        IPage<WordRow> result = wordMapper.selectWordPage(page, wordbookId, keyword);
        List<WordResponse> records = result.getRecords().stream()
                .map(WordResponse::from)
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public WordResponse lookupWord(Long wordbookId, String text) {
        getEnabledWordbook(wordbookId);
        String normalizedText = normalizeLookupText(text);
        String compactText = normalizedText.replaceAll("[^a-z0-9]", "");
        if (!StringUtils.hasText(compactText)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择英文单词或词组");
        }
        WordRow row = wordMapper.selectLookupWord(wordbookId, normalizedText, compactText);
        if (row == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND, "未找到该单词或词组");
        }
        return WordResponse.from(row);
    }

    public Wordbook getEnabledWordbook(Long wordbookId) {
        Wordbook wordbook = wordbookMapper.selectOne(new LambdaQueryWrapper<Wordbook>()
                .eq(Wordbook::getId, wordbookId)
                .eq(Wordbook::getEnabled, true)
                .last("LIMIT 1"));
        if (wordbook == null) {
            throw new BizException(ErrorCode.WORDBOOK_NOT_FOUND);
        }
        return wordbook;
    }

    private String normalizeLookupText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择英文单词或词组");
        }
        String normalized = text
                .replace('’', '\'')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("^([^a-z]+)|([^a-z]+)$", "");
        if (!normalized.matches("[a-z]+(?:['-][a-z]+)?(?:[ -]+[a-z]+(?:['-][a-z]+)?)*")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择英文单词或词组");
        }
        return normalized;
    }
}
