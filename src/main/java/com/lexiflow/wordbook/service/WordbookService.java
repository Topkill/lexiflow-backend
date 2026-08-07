package com.lexiflow.wordbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.dto.LexiflowDictionaryEntryRow;
import com.lexiflow.wordbook.dto.WordQueryRequest;
import com.lexiflow.wordbook.dto.WordResponse;
import com.lexiflow.wordbook.dto.WordRow;
import com.lexiflow.wordbook.dto.WordbookQueryRequest;
import com.lexiflow.wordbook.dto.WordbookResponse;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 词库服务。
 * <p>提供词库列表查询、词库详情、单词分页查询、精确查词等功能。</p>
 */
@Service
@RequiredArgsConstructor
public class WordbookService {

    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final ObjectMapper objectMapper;

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
        Page<WordRow> page = Page.of(request.page(), request.size());
        String keyword = StringUtils.hasText(request.keyword()) ? request.keyword().trim() : null;
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
        if (row != null) {
            return WordResponse.from(row);
        }

        WordRow globalRow = wordMapper.selectLookupWordInEnabledWordbooks(normalizedText, compactText);
        if (globalRow != null) {
            return WordResponse.from(globalRow);
        }

        WordResponse dictionaryResponse = lookupLexiflowDictionary(normalizedText, compactText);
        if (dictionaryResponse != null) {
            return dictionaryResponse;
        }

        throw new BizException(ErrorCode.WORD_NOT_FOUND, "未找到该单词或词组");
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

    private WordResponse lookupLexiflowDictionary(String normalizedText, String compactText) {
        LexiflowDictionaryEntryRow row;
        try {
            row = wordMapper.selectLookupDictionaryEntry(normalizedText, compactText);
        } catch (DataAccessException ex) {
            return null;
        }
        if (row == null) {
            return null;
        }

        List<String> phonetics = parseStringArray(row.phoneticsJson());
        return new WordResponse(
                "dict:" + row.id(),
                row.word(),
                row.normalizedWord(),
                phonetics.isEmpty() ? null : phonetics.get(0),
                phonetics.size() > 1 ? phonetics.get(1) : null,
                buildDictionaryTrans(row.sensesJson()),
                buildDictionarySentences(row.sensesJson()),
                null,
                null,
                null,
                null,
                row.primaryPos(),
                row.primaryDefinition(),
                row.source(),
                null,
                null,
                null
        );
    }

    private List<String> parseStringArray(String json) {
        JsonNode root = readJson(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        root.forEach(node -> {
            if (StringUtils.hasText(node.asText())) {
                values.add(node.asText().trim());
            }
        });
        return values;
    }

    private String buildDictionaryTrans(String sensesJson) {
        JsonNode senses = readJson(sensesJson);
        if (senses == null || !senses.isArray()) {
            return null;
        }
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode sense : senses) {
            JsonNode definitions = sense.path(1);
            if (!definitions.isArray()) {
                continue;
            }
            ArrayNode definitionNodes = objectMapper.createArrayNode();
            definitions.forEach(definition -> {
                if (StringUtils.hasText(definition.asText())) {
                    definitionNodes.add(definition.asText().trim());
                }
            });
            if (definitionNodes.isEmpty()) {
                continue;
            }
            ObjectNode item = objectMapper.createObjectNode();
            if (StringUtils.hasText(sense.path(0).asText())) {
                item.put("pos", sense.path(0).asText().trim());
            }
            item.set("definitions", definitionNodes);
            result.add(item);
        }
        return result.isEmpty() ? null : writeJson(result);
    }

    private String buildDictionarySentences(String sensesJson) {
        JsonNode senses = readJson(sensesJson);
        if (senses == null || !senses.isArray()) {
            return null;
        }
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode sense : senses) {
            JsonNode example = sense.path(2);
            if (!example.isArray() || example.size() < 2) {
                continue;
            }
            String english = example.path(0).asText("");
            String chinese = example.path(1).asText("");
            if (!StringUtils.hasText(english) && !StringUtils.hasText(chinese)) {
                continue;
            }
            ObjectNode item = objectMapper.createObjectNode();
            item.put("c", StringUtils.hasText(english) ? english.trim() : "");
            item.put("cn", StringUtils.hasText(chinese) ? chinese.trim() : "");
            result.add(item);
        }
        return result.isEmpty() ? null : writeJson(result);
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return null;
        }
    }
}
