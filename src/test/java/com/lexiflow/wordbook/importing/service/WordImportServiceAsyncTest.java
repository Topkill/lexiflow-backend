package com.lexiflow.wordbook.importing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.importing.domain.WordImportDuplicateStrategy;
import com.lexiflow.wordbook.importing.domain.WordImportSourceType;
import com.lexiflow.wordbook.importing.domain.WordImportStatus;
import com.lexiflow.wordbook.importing.domain.WordImportTask;
import com.lexiflow.wordbook.importing.dto.WordImportJsonUrlRequest;
import com.lexiflow.wordbook.importing.dto.WordImportTaskResponse;
import com.lexiflow.wordbook.importing.mapper.WordImportErrorMapper;
import com.lexiflow.wordbook.importing.mapper.WordImportTaskMapper;
import com.lexiflow.wordbook.importing.mq.WordImportTaskPublisher;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import com.lexiflow.wordbook.service.WordDictionaryJsonService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class WordImportServiceAsyncTest {

    private static final long IMPORT_TASK_ID = 990001L;

    @Mock
    private WordbookMapper wordbookMapper;
    @Mock
    private WordMapper wordMapper;
    @Mock
    private WordImportTaskMapper wordImportTaskMapper;
    @Mock
    private WordImportErrorMapper wordImportErrorMapper;
    @Mock
    private WordDictionaryJsonService wordDictionaryJsonService;
    @Mock
    private WordImportTaskPublisher wordImportTaskPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WordImportService wordImportService;

    @BeforeEach
    void setUp() {
        wordImportService = new WordImportService(
                wordbookMapper,
                wordMapper,
                wordImportTaskMapper,
                wordImportErrorMapper,
                objectMapper,
                wordDictionaryJsonService,
                wordImportTaskPublisher,
                transactionTemplate
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(Path.of("data", "imports", String.valueOf(IMPORT_TASK_ID), "words.xlsx"));
        Files.deleteIfExists(Path.of("data", "imports", String.valueOf(IMPORT_TASK_ID)));
    }

    @Test
    void importWordsShouldCreatePendingTaskAndPublishMessage() throws Exception {
        when(wordbookMapper.selectById(1L)).thenReturn(wordbook());
        doAnswer(invocation -> {
            WordImportTask task = invocation.getArgument(0);
            task.setId(IMPORT_TASK_ID);
            return 1;
        }).when(wordImportTaskMapper).insert(any(WordImportTask.class));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "words.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-empty".getBytes()
        );

        WordImportTaskResponse response = wordImportService.importWords(7L, 1L, WordImportDuplicateStrategy.OVERWRITE, file);

        ArgumentCaptor<WordImportTask> updateCaptor = ArgumentCaptor.forClass(WordImportTask.class);
        verify(wordImportTaskMapper).updateById(updateCaptor.capture());
        WordImportTask savedTask = updateCaptor.getValue();
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(savedTask.getSourceType()).isEqualTo(WordImportSourceType.EXCEL);
        assertThat(savedTask.getStatus()).isEqualTo(WordImportStatus.PENDING);
        assertThat(savedTask.getFilePath()).endsWith("words.xlsx");
        verify(wordImportTaskPublisher).publish(IMPORT_TASK_ID);
    }

    @Test
    void importWordsFromJsonUrlShouldPersistReplaceOptionAndPublishMessage() throws Exception {
        when(wordbookMapper.selectById(1L)).thenReturn(wordbook());
        doAnswer(invocation -> {
            WordImportTask task = invocation.getArgument(0);
            task.setId(IMPORT_TASK_ID);
            return 1;
        }).when(wordImportTaskMapper).insert(any(WordImportTask.class));

        WordImportTaskResponse response = wordImportService.importWordsFromJsonUrl(
                7L,
                1L,
                new WordImportJsonUrlRequest("https://example.com/words.json", WordImportDuplicateStrategy.SKIP, true)
        );

        ArgumentCaptor<WordImportTask> insertCaptor = ArgumentCaptor.forClass(WordImportTask.class);
        verify(wordImportTaskMapper).insert(insertCaptor.capture());
        WordImportTask task = insertCaptor.getValue();
        JsonNode requestJson = objectMapper.readTree(task.getRequestJson());
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(task.getSourceType()).isEqualTo(WordImportSourceType.JSON_URL);
        assertThat(requestJson.path("replaceWordbook").asBoolean()).isTrue();
        assertThat(requestJson.path("sourceUrl").asText()).isEqualTo("https://example.com/words.json");
        verify(wordImportTaskPublisher).publish(IMPORT_TASK_ID);
    }

    @Test
    void importWordsFromJsonUrlShouldMarkFailedWhenPublishFails() {
        when(wordbookMapper.selectById(1L)).thenReturn(wordbook());
        doAnswer(invocation -> {
            WordImportTask task = invocation.getArgument(0);
            task.setId(IMPORT_TASK_ID);
            return 1;
        }).when(wordImportTaskMapper).insert(any(WordImportTask.class));
        doThrow(new AmqpException("rabbit down")).when(wordImportTaskPublisher).publish(IMPORT_TASK_ID);

        assertThatThrownBy(() -> wordImportService.importWordsFromJsonUrl(
                7L,
                1L,
                new WordImportJsonUrlRequest("https://example.com/words.json", WordImportDuplicateStrategy.SKIP, false)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        ArgumentCaptor<WordImportTask> updateCaptor = ArgumentCaptor.forClass(WordImportTask.class);
        verify(wordImportTaskMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(WordImportStatus.FAILED);
    }

    @Test
    void processImportTaskShouldSkipRunningTaskWhenMessageIsNotRedelivered() {
        WordImportTask task = importTask(WordImportStatus.RUNNING);
        when(wordImportTaskMapper.selectById(IMPORT_TASK_ID)).thenReturn(task);

        wordImportService.processImportTask(IMPORT_TASK_ID, false);

        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void processImportTaskShouldRecoverRunningTaskWhenMessageIsRedelivered() {
        WordImportTask task = importTask(WordImportStatus.RUNNING);
        when(wordImportTaskMapper.selectById(IMPORT_TASK_ID)).thenReturn(task);

        wordImportService.processImportTask(IMPORT_TASK_ID, true);

        verify(transactionTemplate).executeWithoutResult(any());
    }

    private Wordbook wordbook() {
        Wordbook wordbook = new Wordbook();
        wordbook.setId(1L);
        wordbook.setWordCount(0);
        return wordbook;
    }

    private WordImportTask importTask(WordImportStatus status) {
        WordImportTask task = new WordImportTask();
        task.setId(IMPORT_TASK_ID);
        task.setWordbookId(1L);
        task.setFileName("words.xlsx");
        task.setFilePath("data/imports/words.xlsx");
        task.setSourceType(WordImportSourceType.EXCEL);
        task.setStatus(status);
        task.setDuplicateStrategy(WordImportDuplicateStrategy.SKIP);
        task.setCreatedBy(7L);
        return task;
    }
}
