package com.lexiflow.wordbook.importing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.domain.WordbookWord;
import com.lexiflow.wordbook.importing.domain.WordImportDuplicateStrategy;
import com.lexiflow.wordbook.importing.domain.WordImportError;
import com.lexiflow.wordbook.importing.domain.WordImportStatus;
import com.lexiflow.wordbook.importing.domain.WordImportTask;
import com.lexiflow.wordbook.importing.dto.WordImportErrorQueryRequest;
import com.lexiflow.wordbook.importing.dto.WordImportJsonUrlRequest;
import com.lexiflow.wordbook.importing.dto.WordImportErrorResponse;
import com.lexiflow.wordbook.importing.dto.WordImportTaskResponse;
import com.lexiflow.wordbook.importing.dto.WordImportTemplateResponse;
import com.lexiflow.wordbook.importing.mapper.WordImportErrorMapper;
import com.lexiflow.wordbook.importing.mapper.WordImportTaskMapper;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import com.lexiflow.wordbook.mapper.WordbookWordMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.lexiflow.wordbook.service.WordDictionaryJsonService;

@Service
@RequiredArgsConstructor
public class WordImportService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final String[] HEADERS = {"单词", "音标", "词性", "中文释义", "英文例句", "例句翻译", "难度", "标签"};
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final int JSON_URL_TIMEOUT_SECONDS = 60;
    private static final int MAX_JSON_WORDS = 20000;

    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final WordbookWordMapper wordbookWordMapper;
    private final WordImportTaskMapper wordImportTaskMapper;
    private final WordImportErrorMapper wordImportErrorMapper;
    private final ObjectMapper objectMapper;
    private final WordDictionaryJsonService wordDictionaryJsonService;
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public WordImportTemplateResponse buildTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("words");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                sheet.setColumnWidth(i, 22 * 256);
            }
            Row demo = sheet.createRow(1);
            demo.createCell(0).setCellValue("ability");
            demo.createCell(1).setCellValue("/əˈbɪləti/");
            demo.createCell(2).setCellValue("n.");
            demo.createCell(3).setCellValue("能力；才能");
            demo.createCell(4).setCellValue("Practice can improve your ability to remember words.");
            demo.createCell(5).setCellValue("练习可以提高你记单词的能力。");
            demo.createCell(6).setCellValue("2");
            demo.createCell(7).setCellValue("core,exam");
            workbook.write(outputStream);
            return new WordImportTemplateResponse("lexiflow-word-import-template.xlsx", outputStream.toByteArray());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成导入模板失败");
        }
    }

    @Transactional
    public WordImportTaskResponse importWords(Long adminUserId, Long wordbookId, WordImportDuplicateStrategy duplicateStrategy, MultipartFile file) {
        Wordbook wordbook = getWordbook(wordbookId);
        validateFile(file);
        WordImportTask task = createTask(adminUserId, wordbookId, duplicateStrategy, file);
        try {
            task.setStatus(WordImportStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            wordImportTaskMapper.updateById(task);
            Path savedFile = saveUploadFile(task.getId(), file);
            task.setFilePath(savedFile.toString());
            ImportResult result = parseAndImport(task, adminUserId, wordbook, duplicateStrategy, file);
            task.setTotalRows(result.totalRows());
            task.setSuccessRows(result.successRows());
            task.setFailedRows(result.failedRows());
            task.setStatus(result.failedRows() == 0 ? WordImportStatus.SUCCESS : WordImportStatus.PARTIAL_SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            wordImportTaskMapper.updateById(task);
            refreshWordbookCount(wordbookId);
            return WordImportTaskResponse.from(task);
        } catch (BizException ex) {
            markFailed(task, ex.getCustomMessage());
            throw ex;
        } catch (Exception ex) {
            markFailed(task, ex.getMessage());
            throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID, "Excel 导入失败");
        }
    }

    @Transactional
    public WordImportTaskResponse importWordsFromJsonUrl(Long adminUserId, Long wordbookId, WordImportJsonUrlRequest request) {
        Wordbook wordbook = getWordbook(wordbookId);
        URI sourceUri = validateJsonUrl(request.sourceUrl());
        WordImportDuplicateStrategy duplicateStrategy = request.safeDuplicateStrategy();
        WordImportTask task = createJsonUrlTask(adminUserId, wordbookId, duplicateStrategy, sourceUri);
        try {
            task.setStatus(WordImportStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            wordImportTaskMapper.updateById(task);
            if (request.shouldReplaceWordbook()) {
                clearWordbookRelations(wordbookId);
            }
            JsonNode root = fetchJson(sourceUri);
            if (!root.isArray()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "远程 JSON 必须是单词数组");
            }
            if (root.size() > MAX_JSON_WORDS) {
                throw new BizException(ErrorCode.BAD_REQUEST, "JSON 单词数量不能超过 " + MAX_JSON_WORDS);
            }
            ImportResult result = importJsonWords(task, adminUserId, wordbook, duplicateStrategy, root);
            task.setTotalRows(result.totalRows());
            task.setSuccessRows(result.successRows());
            task.setFailedRows(result.failedRows());
            task.setStatus(result.failedRows() == 0 ? WordImportStatus.SUCCESS : WordImportStatus.PARTIAL_SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            wordImportTaskMapper.updateById(task);
            refreshWordbookCount(wordbookId);
            return WordImportTaskResponse.from(task);
        } catch (BizException ex) {
            markFailed(task, ex.getCustomMessage());
            throw ex;
        } catch (Exception ex) {
            markFailed(task, ex.getMessage());
            throw new BizException(ErrorCode.BAD_REQUEST, "JSON URL 导入失败");
        }
    }

    public WordImportTaskResponse getTask(Long importTaskId) {
        WordImportTask task = wordImportTaskMapper.selectById(importTaskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        return WordImportTaskResponse.from(task);
    }

    public PageResponse<WordImportErrorResponse> pageErrors(Long importTaskId, WordImportErrorQueryRequest request) {
        getTask(importTaskId);
        WordImportErrorQueryRequest safeRequest = request == null ? new WordImportErrorQueryRequest(null, null) : request;
        Page<WordImportError> page = wordImportErrorMapper.selectPage(
                Page.of(safeRequest.safePage(), safeRequest.safeSize()),
                new LambdaQueryWrapper<WordImportError>()
                        .eq(WordImportError::getImportTaskId, importTaskId)
                        .orderByAsc(WordImportError::getRowNo)
                        .orderByAsc(WordImportError::getId)
        );
        return PageResponse.of(page.getRecords().stream().map(WordImportErrorResponse::from).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public byte[] buildErrorReport(Long importTaskId) {
        getTask(importTaskId);
        List<WordImportError> errors = wordImportErrorMapper.selectList(new LambdaQueryWrapper<WordImportError>()
                .eq(WordImportError::getImportTaskId, importTaskId)
                .orderByAsc(WordImportError::getRowNo));
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("errors");
            Row header = sheet.createRow(0);
            String[] headers = {"行号", "单词", "错误码", "错误说明", "原始数据"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 24 * 256);
            }
            for (int i = 0; i < errors.size(); i++) {
                WordImportError error = errors.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(error.getRowNo());
                row.createCell(1).setCellValue(error.getWordText());
                row.createCell(2).setCellValue(error.getErrorCode());
                row.createCell(3).setCellValue(error.getErrorMessage());
                row.createCell(4).setCellValue(error.getRawData());
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成错误报告失败");
        }
    }

    private WordImportTask createTask(Long adminUserId, Long wordbookId, WordImportDuplicateStrategy duplicateStrategy, MultipartFile file) {
        WordImportTask task = new WordImportTask();
        task.setWordbookId(wordbookId);
        task.setFileName(file.getOriginalFilename() == null ? "words.xlsx" : file.getOriginalFilename());
        task.setFilePath("");
        task.setDuplicateStrategy(duplicateStrategy == null ? WordImportDuplicateStrategy.SKIP : duplicateStrategy);
        task.setStatus(WordImportStatus.PENDING);
        task.setTotalRows(0);
        task.setSuccessRows(0);
        task.setFailedRows(0);
        task.setCreatedBy(adminUserId);
        wordImportTaskMapper.insert(task);
        return task;
    }

    private WordImportTask createJsonUrlTask(Long adminUserId, Long wordbookId, WordImportDuplicateStrategy duplicateStrategy, URI sourceUri) {
        WordImportTask task = new WordImportTask();
        task.setWordbookId(wordbookId);
        task.setFileName(sourceUri.toString());
        task.setFilePath(sourceUri.toString());
        task.setDuplicateStrategy(duplicateStrategy == null ? WordImportDuplicateStrategy.SKIP : duplicateStrategy);
        task.setStatus(WordImportStatus.PENDING);
        task.setTotalRows(0);
        task.setSuccessRows(0);
        task.setFailedRows(0);
        task.setCreatedBy(adminUserId);
        wordImportTaskMapper.insert(task);
        return task;
    }

    private ImportResult parseAndImport(WordImportTask task, Long adminUserId, Wordbook wordbook, WordImportDuplicateStrategy duplicateStrategy, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            validateHeader(sheet);
            int totalRows = Math.max(0, sheet.getLastRowNum());
            if (totalRows > MAX_ROWS) {
                throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID, "导入行数不能超过 5000 行");
            }
            int successRows = 0;
            int failedRows = 0;
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row)) {
                    continue;
                }
                ImportRow importRow = readRow(row);
                try {
                    importOneRow(adminUserId, wordbook, duplicateStrategy, importRow);
                    successRows++;
                } catch (BizException ex) {
                    failedRows++;
                    saveError(task.getId(), rowIndex + 1, importRow.wordText(), "ROW_INVALID", ex.getCustomMessage(), importRow.rawData());
                } catch (Exception ex) {
                    failedRows++;
                    saveError(task.getId(), rowIndex + 1, importRow.wordText(), "ROW_ERROR", "行数据导入失败", importRow.rawData());
                }
            }
            return new ImportResult(successRows + failedRows, successRows, failedRows);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID, "Excel 模板格式错误");
        }
    }

    private void importOneRow(Long adminUserId, Wordbook wordbook, WordImportDuplicateStrategy duplicateStrategy, ImportRow row) {
        if (!StringUtils.hasText(row.wordText())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单词不能为空");
        }
        if (!StringUtils.hasText(row.definition())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "中文释义不能为空");
        }
        int difficulty = parseDifficulty(row.difficulty());
        String normalizedWord = wordDictionaryJsonService.normalizeWord(row.wordText());
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getNormalizedWord, normalizedWord)
                .last("LIMIT 1"));
        boolean newWord = word == null;
        if (newWord) {
            word = new Word();
            word.setWord(row.wordText().trim());
            word.setNormalizedWord(normalizedWord);
            word.setCreatedBy(adminUserId);
            word.setDeleted(0);
            word.setVersion(0);
        }
        if (newWord || duplicateStrategy == WordImportDuplicateStrategy.OVERWRITE) {
            fillWord(word, row, difficulty, adminUserId, true);
        } else if (duplicateStrategy == WordImportDuplicateStrategy.FILL_EMPTY) {
            fillWord(word, row, difficulty, adminUserId, false);
        }
        if (newWord) {
            wordMapper.insert(word);
        } else if (duplicateStrategy != WordImportDuplicateStrategy.SKIP) {
            wordMapper.updateById(word);
        }

        WordbookWord relation = wordbookWordMapper.selectOne(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbook.getId())
                .eq(WordbookWord::getWordId, word.getId())
                .last("LIMIT 1"));
        if (relation != null && duplicateStrategy == WordImportDuplicateStrategy.SKIP) {
            throw new BizException(ErrorCode.CONFLICT, "单词已存在，已按策略跳过");
        }
        if (relation == null) {
            relation = new WordbookWord();
            relation.setWordbookId(wordbook.getId());
            relation.setWordId(word.getId());
            relation.setSequenceNo(nextSequenceNo(wordbook.getId()));
            relation.setDeleted(0);
            relation.setVersion(0);
        }
        if (duplicateStrategy != WordImportDuplicateStrategy.FILL_EMPTY || relation.getDifficultyLevel() == null) {
            relation.setDifficultyLevel(difficulty);
        }
        relation.setExamFrequency(relation.getExamFrequency() == null ? 0 : relation.getExamFrequency());
        relation.setEnabled(true);
        if (relation.getId() == null) {
            wordbookWordMapper.insert(relation);
        } else {
            wordbookWordMapper.updateById(relation);
        }
    }

    private void fillWord(Word word, ImportRow row, int difficulty, Long adminUserId, boolean overwrite) {
        setString(word::setWord, word.getWord(), row.wordText(), overwrite);
        setString(word::setPhonetic0, word.getPhonetic0(), row.phonetic(), overwrite);
        setString(word::setPhonetic1, word.getPhonetic1(), row.phonetic(), overwrite);
        String trans = wordDictionaryJsonService.buildTransJson(row.pos(), row.definition());
        setString(word::setTrans, word.getTrans(), trans, overwrite);
        String sentences = wordDictionaryJsonService.buildSentencesJson(row.exampleSentence(), row.exampleTranslation());
        setString(word::setSentences, word.getSentences(), sentences, overwrite);
        WordDictionaryJsonService.WordSummary summary = wordDictionaryJsonService.deriveSummary(trans, row.pos(), row.definition());
        setString(word::setPrimaryPos, word.getPrimaryPos(), summary.primaryPos(), overwrite);
        setString(word::setPrimaryDefinition, word.getPrimaryDefinition(), summary.primaryDefinition(), overwrite);
        setString(word::setTags, word.getTags(), row.tags(), overwrite);
        word.setUpdatedBy(adminUserId);
    }

    private ImportResult importJsonWords(WordImportTask task, Long adminUserId, Wordbook wordbook, WordImportDuplicateStrategy duplicateStrategy, JsonNode root) {
        int successRows = 0;
        int failedRows = 0;
        int sequenceNo = 1;
        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            try {
                importOneJsonWord(adminUserId, wordbook, duplicateStrategy, node, sequenceNo++);
                successRows++;
            } catch (BizException ex) {
                failedRows++;
                saveError(task.getId(), i + 1, node.path("word").asText(null), "ROW_INVALID", ex.getCustomMessage(), wordDictionaryJsonService.toJson(node));
            } catch (Exception ex) {
                failedRows++;
                saveError(task.getId(), i + 1, node.path("word").asText(null), "ROW_ERROR", "JSON 行数据导入失败", wordDictionaryJsonService.toJson(node));
            }
        }
        return new ImportResult(successRows + failedRows, successRows, failedRows);
    }

    private void importOneJsonWord(Long adminUserId, Wordbook wordbook, WordImportDuplicateStrategy duplicateStrategy, JsonNode node, int sequenceNo) {
        String wordText = node.path("word").asText(null);
        if (!StringUtils.hasText(wordText)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "word 不能为空");
        }
        JsonNode transNode = node.path("trans");
        if (!transNode.isArray() || transNode.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "trans 不能为空");
        }
        String normalizedWord = wordDictionaryJsonService.normalizeWord(wordText);
        Word word = wordMapper.selectOne(new LambdaQueryWrapper<Word>()
                .eq(Word::getNormalizedWord, normalizedWord)
                .last("LIMIT 1"));
        boolean newWord = word == null;
        if (newWord) {
            word = new Word();
            word.setWord(wordText.trim());
            word.setNormalizedWord(normalizedWord);
            word.setCreatedBy(adminUserId);
            word.setDeleted(0);
            word.setVersion(0);
        }
        if (newWord || duplicateStrategy == WordImportDuplicateStrategy.OVERWRITE) {
            fillWordFromJson(word, node, adminUserId, true);
        } else if (duplicateStrategy == WordImportDuplicateStrategy.FILL_EMPTY) {
            fillWordFromJson(word, node, adminUserId, false);
        }
        if (newWord) {
            wordMapper.insert(word);
        } else if (duplicateStrategy != WordImportDuplicateStrategy.SKIP) {
            wordMapper.updateById(word);
        }

        WordbookWord relation = wordbookWordMapper.selectOne(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbook.getId())
                .eq(WordbookWord::getWordId, word.getId())
                .last("LIMIT 1"));
        if (relation != null && duplicateStrategy == WordImportDuplicateStrategy.SKIP) {
            throw new BizException(ErrorCode.CONFLICT, "单词已存在，已按策略跳过");
        }
        if (relation == null) {
            relation = new WordbookWord();
            relation.setWordbookId(wordbook.getId());
            relation.setWordId(word.getId());
            relation.setDeleted(0);
            relation.setVersion(0);
        }
        relation.setSequenceNo(sequenceNo);
        relation.setDifficultyLevel(relation.getDifficultyLevel() == null ? 1 : relation.getDifficultyLevel());
        relation.setExamFrequency(relation.getExamFrequency() == null ? 0 : relation.getExamFrequency());
        relation.setEnabled(true);
        if (relation.getId() == null) {
            wordbookWordMapper.insert(relation);
        } else {
            wordbookWordMapper.updateById(relation);
        }
    }

    private void fillWordFromJson(Word word, JsonNode node, Long adminUserId, boolean overwrite) {
        String trans = wordDictionaryJsonService.toJson(node.path("trans"));
        setString(word::setWord, word.getWord(), node.path("word").asText(), overwrite);
        setString(word::setPhonetic0, word.getPhonetic0(), textOrNull(node, "phonetic0"), overwrite);
        setString(word::setPhonetic1, word.getPhonetic1(), textOrNull(node, "phonetic1"), overwrite);
        setString(word::setTrans, word.getTrans(), trans, overwrite);
        setString(word::setSentences, word.getSentences(), optionalJson(node, "sentences"), overwrite);
        setString(word::setPhrases, word.getPhrases(), optionalJson(node, "phrases"), overwrite);
        setString(word::setSynos, word.getSynos(), optionalJson(node, "synos"), overwrite);
        setString(word::setRelWords, word.getRelWords(), optionalJson(node, "relWords"), overwrite);
        setString(word::setEtymology, word.getEtymology(), optionalJson(node, "etymology"), overwrite);
        WordDictionaryJsonService.WordSummary summary = wordDictionaryJsonService.deriveSummary(trans, null, null);
        setString(word::setPrimaryPos, word.getPrimaryPos(), summary.primaryPos(), overwrite);
        setString(word::setPrimaryDefinition, word.getPrimaryDefinition(), summary.primaryDefinition(), overwrite);
        word.setUpdatedBy(adminUserId);
    }

    private void setString(java.util.function.Consumer<String> setter, String currentValue, String newValue, boolean overwrite) {
        if (!StringUtils.hasText(newValue)) {
            return;
        }
        if (overwrite || !StringUtils.hasText(currentValue)) {
            setter.accept(newValue.trim());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID, "请选择 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID, "仅支持 .xlsx 文件");
        }
    }

    private Path saveUploadFile(Long taskId, MultipartFile file) throws Exception {
        Path dir = Path.of("data", "imports", String.valueOf(taskId));
        Files.createDirectories(dir);
        String filename = file.getOriginalFilename() == null ? "words.xlsx" : Path.of(file.getOriginalFilename()).getFileName().toString();
        Path target = dir.resolve(filename);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID);
        }
        for (int i = 0; i < HEADERS.length; i++) {
            String actual = cellText(header.getCell(i));
            if (!HEADERS[i].equals(actual)) {
                throw new BizException(ErrorCode.EXCEL_TEMPLATE_INVALID, "模板表头不匹配：第 " + (i + 1) + " 列应为 " + HEADERS[i]);
            }
        }
    }

    private ImportRow readRow(Row row) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (int i = 0; i < HEADERS.length; i++) {
            raw.put(HEADERS[i], cellText(row.getCell(i)));
        }
        return new ImportRow(
                raw.get("单词"),
                raw.get("音标"),
                raw.get("词性"),
                raw.get("中文释义"),
                raw.get("英文例句"),
                raw.get("例句翻译"),
                raw.get("难度"),
                raw.get("标签"),
                toJson(raw)
        );
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < HEADERS.length; i++) {
            if (StringUtils.hasText(cellText(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        return DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private int parseDifficulty(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }
        try {
            int difficulty = Integer.parseInt(value.trim());
            if (difficulty < 1 || difficulty > 5) {
                throw new NumberFormatException();
            }
            return difficulty;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "难度必须是 1-5 的整数");
        }
    }

    private int nextSequenceNo(Long wordbookId) {
        WordbookWord latest = wordbookWordMapper.selectOne(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbookId)
                .orderByDesc(WordbookWord::getSequenceNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getSequenceNo() + 1;
    }

    private void refreshWordbookCount(Long wordbookId) {
        Long count = wordbookWordMapper.selectCount(new LambdaQueryWrapper<WordbookWord>()
                .eq(WordbookWord::getWordbookId, wordbookId)
                .eq(WordbookWord::getEnabled, true));
        Wordbook wordbook = getWordbook(wordbookId);
        wordbook.setWordCount(count.intValue());
        wordbookMapper.updateById(wordbook);
    }

    private Wordbook getWordbook(Long wordbookId) {
        Wordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null) {
            throw new BizException(ErrorCode.WORDBOOK_NOT_FOUND);
        }
        return wordbook;
    }

    private void markFailed(WordImportTask task, String message) {
        task.setStatus(WordImportStatus.FAILED);
        task.setFinishedAt(LocalDateTime.now());
        wordImportTaskMapper.updateById(task);
    }

    private void saveError(Long importTaskId, int rowNo, String wordText, String errorCode, String errorMessage, String rawData) {
        WordImportError error = new WordImportError();
        error.setImportTaskId(importTaskId);
        error.setRowNo(rowNo);
        error.setWordText(StringUtils.hasText(wordText) ? wordText.trim() : null);
        error.setErrorCode(errorCode);
        error.setErrorMessage(errorMessage.length() > 512 ? errorMessage.substring(0, 512) : errorMessage);
        error.setRawData(rawData);
        wordImportErrorMapper.insert(error);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private URI validateJsonUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "JSON URL 不能为空");
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "JSON URL 仅支持 http 或 https");
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "JSON URL 格式不正确");
        }
    }

    private JsonNode fetchJson(URI sourceUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(sourceUri)
                    .timeout(java.time.Duration.ofSeconds(JSON_URL_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.BAD_REQUEST, "远程 JSON 下载失败，状态码：" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "远程 JSON 下载或解析失败");
        }
    }

    private void clearWordbookRelations(Long wordbookId) {
        wordbookWordMapper.physicalDeleteByWordbookId(wordbookId);
    }

    private String optionalJson(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : wordDictionaryJsonService.toJson(value);
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText()) ? null : value.asText().trim();
    }

    private record ImportRow(
            String wordText,
            String phonetic,
            String pos,
            String definition,
            String exampleSentence,
            String exampleTranslation,
            String difficulty,
            String tags,
            String rawData
    ) {
    }

    private record ImportResult(Integer totalRows, Integer successRows, Integer failedRows) {
    }
}
