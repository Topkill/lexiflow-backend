package com.lexiflow.quiz.cloze.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClozeAccuracyAggregate {

    private Long totalBlanks;
    private Long correctCount;
}
