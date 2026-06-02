package com.lexiflow.quiz.cloze.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lexiflow.quiz.cloze.service.ClozeQuizService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClozeGenerationTaskConsumerTest {

    @Mock
    private ClozeQuizService clozeQuizService;

    @Test
    void handleShouldDispatchClozeTask() {
        ClozeGenerationTaskConsumer consumer = new ClozeGenerationTaskConsumer(clozeQuizService);

        consumer.handle(new ClozeGenerationTaskMessage(12L), false);

        verify(clozeQuizService).processClozeTask(12L, false);
    }

    @Test
    void handleShouldPassRedeliveredFlag() {
        ClozeGenerationTaskConsumer consumer = new ClozeGenerationTaskConsumer(clozeQuizService);

        consumer.handle(new ClozeGenerationTaskMessage(12L), true);

        verify(clozeQuizService).processClozeTask(12L, true);
    }

    @Test
    void handleShouldIgnoreEmptyMessage() {
        ClozeGenerationTaskConsumer consumer = new ClozeGenerationTaskConsumer(clozeQuizService);

        consumer.handle(new ClozeGenerationTaskMessage(null), false);

        verifyNoInteractions(clozeQuizService);
    }

    @Test
    void handleShouldRethrowProcessingException() {
        ClozeGenerationTaskConsumer consumer = new ClozeGenerationTaskConsumer(clozeQuizService);
        doThrow(new IllegalStateException("boom")).when(clozeQuizService).processClozeTask(12L, false);

        assertThatThrownBy(() -> consumer.handle(new ClozeGenerationTaskMessage(12L), false))
                .isInstanceOf(IllegalStateException.class);

        verify(clozeQuizService).processClozeTask(12L, false);
    }
}
