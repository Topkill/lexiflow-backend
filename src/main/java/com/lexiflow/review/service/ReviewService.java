package com.lexiflow.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.review.dto.FavoriteWordRequest;
import com.lexiflow.review.dto.FavoriteWordResponse;
import com.lexiflow.review.dto.ReviewQueryRequest;
import com.lexiflow.review.dto.ReviewWordResponse;
import com.lexiflow.review.dto.WrongWordResponse;
import com.lexiflow.study.progress.domain.FavoriteWord;
import com.lexiflow.study.progress.domain.UserWordState;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.FavoriteWordMapper;
import com.lexiflow.study.progress.mapper.UserWordStateMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final UserWordStateMapper userWordStateMapper;
    private final WrongWordMapper wrongWordMapper;
    private final FavoriteWordMapper favoriteWordMapper;
    private final WordMapper wordMapper;
    private final WordbookService wordbookService;

    public PageResponse<ReviewWordResponse> pageDueWords(Long userId, ReviewQueryRequest request) {
        LambdaQueryWrapper<UserWordState> wrapper = new LambdaQueryWrapper<UserWordState>()
                .eq(UserWordState::getUserId, userId)
                .eq(UserWordState::getLearned, true)
                .isNotNull(UserWordState::getNextReviewDate)
                .le(UserWordState::getNextReviewDate, LocalDate.now())
                .orderByAsc(UserWordState::getNextReviewDate)
                .orderByDesc(UserWordState::getUpdatedAt);
        if (request.wordbookId() != null) {
            wrapper.eq(UserWordState::getWordbookId, request.wordbookId());
        }
        Page<UserWordState> page = userWordStateMapper.selectPage(Page.of(request.page(), request.size()), wrapper);
        Map<Long, Word> wordMap = loadWords(page.getRecords().stream().map(UserWordState::getWordId).toList());
        List<ReviewWordResponse> records = page.getRecords().stream()
                .map(state -> ReviewWordResponse.from(state, requireWord(wordMap, state.getWordId())))
                .toList();
        return PageResponse.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResponse<WrongWordResponse> pageWrongWords(Long userId, ReviewQueryRequest request) {
        LambdaQueryWrapper<WrongWord> wrapper = new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getResolved, false);
        if (request.wordbookId() != null) {
            wrapper.eq(WrongWord::getWordbookId, request.wordbookId());
        }
        boolean asc = "asc".equalsIgnoreCase(request.sortOrder());
        if ("lastWrongAt".equalsIgnoreCase(request.sortBy())) {
            wrapper.orderBy(true, asc, WrongWord::getLastWrongAt);
        } else {
            wrapper.orderBy(true, asc, WrongWord::getWrongCount);
            wrapper.orderByDesc(WrongWord::getLastWrongAt);
        }
        Page<WrongWord> page = wrongWordMapper.selectPage(Page.of(request.page(), request.size()), wrapper);
        Map<Long, Word> wordMap = loadWords(page.getRecords().stream().map(WrongWord::getWordId).toList());
        List<WrongWordResponse> records = page.getRecords().stream()
                .map(wrongWord -> WrongWordResponse.from(wrongWord, requireWord(wordMap, wrongWord.getWordId())))
                .toList();
        return PageResponse.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public void resolveWrongWord(Long userId, Long wrongWordId) {
        WrongWord wrongWord = wrongWordMapper.selectOne(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getId, wrongWordId)
                .eq(WrongWord::getUserId, userId)
                .last("LIMIT 1"));
        if (wrongWord == null) {
            throw new BizException(ErrorCode.WRONG_WORD_NOT_FOUND);
        }
        wrongWord.setResolved(true);
        wrongWord.setResolvedAt(LocalDateTime.now());
        wrongWordMapper.updateById(wrongWord);
    }

    public PageResponse<FavoriteWordResponse> pageFavoriteWords(Long userId, ReviewQueryRequest request) {
        LambdaQueryWrapper<FavoriteWord> wrapper = new LambdaQueryWrapper<FavoriteWord>()
                .eq(FavoriteWord::getUserId, userId)
                .orderByDesc(FavoriteWord::getCreatedAt);
        if (request.wordbookId() != null) {
            wrapper.eq(FavoriteWord::getWordbookId, request.wordbookId());
        }
        Page<FavoriteWord> page = favoriteWordMapper.selectPage(Page.of(request.page(), request.size()), wrapper);
        Map<Long, Word> wordMap = loadWords(page.getRecords().stream().map(FavoriteWord::getWordId).toList());
        List<FavoriteWordResponse> records = page.getRecords().stream()
                .map(favoriteWord -> FavoriteWordResponse.from(favoriteWord, requireWord(wordMap, favoriteWord.getWordId())))
                .toList();
        return PageResponse.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public FavoriteWordResponse favoriteWord(Long userId, FavoriteWordRequest request) {
        wordbookService.getEnabledWordbook(request.wordbookId());
        Word word = requireWord(wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getId, request.wordId())
                .eq(Word::getWordbookId, request.wordbookId())
                .eq(Word::getEnabled, true)
                .last("LIMIT 1")));
        FavoriteWord favoriteWord = favoriteWordMapper.selectOne(new LambdaQueryWrapper<FavoriteWord>()
                .eq(FavoriteWord::getUserId, userId)
                .eq(FavoriteWord::getWordbookId, request.wordbookId())
                .eq(FavoriteWord::getWordId, request.wordId())
                .last("LIMIT 1"));
        if (favoriteWord == null) {
            favoriteWord = new FavoriteWord();
            favoriteWord.setUserId(userId);
            favoriteWord.setWordbookId(request.wordbookId());
            favoriteWord.setWordId(request.wordId());
            favoriteWord.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
            favoriteWord.setDeleted(0);
            favoriteWordMapper.insert(favoriteWord);
        } else {
            favoriteWord.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
            favoriteWordMapper.updateById(favoriteWord);
        }
        return FavoriteWordResponse.from(favoriteWord, word);
    }

    @Transactional
    public void deleteFavoriteWord(Long userId, Long favoriteWordId) {
        FavoriteWord favoriteWord = favoriteWordMapper.selectOne(new LambdaQueryWrapper<FavoriteWord>()
                .eq(FavoriteWord::getId, favoriteWordId)
                .eq(FavoriteWord::getUserId, userId)
                .last("LIMIT 1"));
        if (favoriteWord == null) {
            throw new BizException(ErrorCode.FAVORITE_WORD_NOT_FOUND);
        }
        favoriteWordMapper.deleteById(favoriteWord.getId());
    }

    private Map<Long, Word> loadWords(List<Long> wordIds) {
        if (wordIds.isEmpty()) {
            return Map.of();
        }
        return wordMapper.selectBatchIds(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
    }

    private Word requireWord(Map<Long, Word> wordMap, Long wordId) {
        return requireWord(wordMap.get(wordId));
    }

    private Word requireWord(Word word) {
        if (word == null) {
            throw new BizException(ErrorCode.WORD_NOT_FOUND);
        }
        return word;
    }
}
