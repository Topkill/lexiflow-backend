package com.lexiflow.quiz.cloze.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateClozeTaskRequestTest {

    @Test
    void regenerateShouldDefaultToFalse() {
        CreateClozeTaskRequest request = new CreateClozeTaskRequest(1L, null, null, null);

        assertThat(request.regenerate()).isFalse();
    }

    @Test
    void regenerateShouldReturnTrueOnlyWhenExplicitlyTrue() {
        assertThat(new CreateClozeTaskRequest(1L, null, null, true).regenerate()).isTrue();
        assertThat(new CreateClozeTaskRequest(1L, null, null, false).regenerate()).isFalse();
    }

    @Test
    void optionalFieldsShouldUseDefaults() {
        CreateClozeTaskRequest request = new CreateClozeTaskRequest(1L, null, null, null);

        assertThat(request.sourceType()).isEqualTo(com.lexiflow.quiz.cloze.domain.ClozeSourceType.MIXED);
        assertThat(request.targetWordCount()).isEqualTo(10);
    }
}
