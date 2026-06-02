package com.lexiflow.report.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lexiflow.report.service.StudyReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyReportTaskConsumerTest {

    @Mock
    private StudyReportService studyReportService;

    @Test
    void handleShouldDispatchReportTask() {
        StudyReportTaskConsumer consumer = new StudyReportTaskConsumer(studyReportService);

        consumer.handle(new StudyReportTaskMessage(12L), false);

        verify(studyReportService).processReportTask(12L, false);
    }

    @Test
    void handleShouldPassRedeliveredFlag() {
        StudyReportTaskConsumer consumer = new StudyReportTaskConsumer(studyReportService);

        consumer.handle(new StudyReportTaskMessage(12L), true);

        verify(studyReportService).processReportTask(12L, true);
    }

    @Test
    void handleShouldIgnoreEmptyMessage() {
        StudyReportTaskConsumer consumer = new StudyReportTaskConsumer(studyReportService);

        consumer.handle(new StudyReportTaskMessage(null), false);

        verifyNoInteractions(studyReportService);
    }

    @Test
    void handleShouldRethrowProcessingException() {
        StudyReportTaskConsumer consumer = new StudyReportTaskConsumer(studyReportService);
        doThrow(new IllegalStateException("boom")).when(studyReportService).processReportTask(12L, false);

        assertThatThrownBy(() -> consumer.handle(new StudyReportTaskMessage(12L), false))
                .isInstanceOf(IllegalStateException.class);

        verify(studyReportService).processReportTask(12L, false);
    }
}
