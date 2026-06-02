package com.lexiflow.wordbook.importing.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lexiflow.wordbook.importing.service.WordImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WordImportTaskConsumerTest {

    @Mock
    private WordImportService wordImportService;

    @Test
    void handleShouldDispatchImportTask() {
        WordImportTaskConsumer consumer = new WordImportTaskConsumer(wordImportService);

        consumer.handle(new WordImportTaskMessage(12L), false);

        verify(wordImportService).processImportTask(12L, false);
    }

    @Test
    void handleShouldPassRedeliveredFlag() {
        WordImportTaskConsumer consumer = new WordImportTaskConsumer(wordImportService);

        consumer.handle(new WordImportTaskMessage(12L), true);

        verify(wordImportService).processImportTask(12L, true);
    }

    @Test
    void handleShouldIgnoreEmptyMessage() {
        WordImportTaskConsumer consumer = new WordImportTaskConsumer(wordImportService);

        consumer.handle(new WordImportTaskMessage(null), false);

        verifyNoInteractions(wordImportService);
    }

    @Test
    void handleShouldRethrowProcessingException() {
        WordImportTaskConsumer consumer = new WordImportTaskConsumer(wordImportService);
        doThrow(new IllegalStateException("boom")).when(wordImportService).processImportTask(12L, false);

        assertThatThrownBy(() -> consumer.handle(new WordImportTaskMessage(12L), false))
                .isInstanceOf(IllegalStateException.class);

        verify(wordImportService).processImportTask(12L, false);
    }
}
