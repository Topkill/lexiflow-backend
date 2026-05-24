package com.lexiflow.quiz.cloze.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreateClozeTaskRequestTest {

    @Test
    void safeRegenerateShouldDefaultToFalse() {
        CreateClozeTaskRequest request = new CreateClozeTaskRequest(1L, null, null, null);

        assertThat(request.safeRegenerate()).isFalse();
    }

    @Test
    void safeRegenerateShouldReturnTrueOnlyWhenExplicitlyTrue() {
        assertThat(new CreateClozeTaskRequest(1L, null, null, true).safeRegenerate()).isTrue();
        assertThat(new CreateClozeTaskRequest(1L, null, null, false).safeRegenerate()).isFalse();
    }
}
