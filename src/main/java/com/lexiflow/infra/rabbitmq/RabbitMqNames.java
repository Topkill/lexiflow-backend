package com.lexiflow.infra.rabbitmq;

public final class RabbitMqNames {

    public static final String WORD_IMPORT_EXCHANGE = "lexiflow.word-import.exchange";
    public static final String WORD_IMPORT_QUEUE = "lexiflow.word-import.queue";
    public static final String WORD_IMPORT_ROUTING_KEY = "lexiflow.word-import.task";
    public static final String WORD_IMPORT_DEAD_LETTER_EXCHANGE = "lexiflow.word-import.dlx";
    public static final String WORD_IMPORT_DEAD_LETTER_QUEUE = "lexiflow.word-import.dlq";
    public static final String WORD_IMPORT_DEAD_LETTER_ROUTING_KEY = "lexiflow.word-import.dead";

    public static final String CLOZE_GENERATION_EXCHANGE = "lexiflow.cloze-generation.exchange";
    public static final String CLOZE_GENERATION_QUEUE = "lexiflow.cloze-generation.queue";
    public static final String CLOZE_GENERATION_ROUTING_KEY = "lexiflow.cloze-generation.task";
    public static final String CLOZE_GENERATION_DEAD_LETTER_EXCHANGE = "lexiflow.cloze-generation.dlx";
    public static final String CLOZE_GENERATION_DEAD_LETTER_QUEUE = "lexiflow.cloze-generation.dlq";
    public static final String CLOZE_GENERATION_DEAD_LETTER_ROUTING_KEY = "lexiflow.cloze-generation.dead";

    public static final String STUDY_REPORT_EXCHANGE = "lexiflow.study-report.exchange";
    public static final String STUDY_REPORT_QUEUE = "lexiflow.study-report.queue";
    public static final String STUDY_REPORT_ROUTING_KEY = "lexiflow.study-report.task";
    public static final String STUDY_REPORT_DEAD_LETTER_EXCHANGE = "lexiflow.study-report.dlx";
    public static final String STUDY_REPORT_DEAD_LETTER_QUEUE = "lexiflow.study-report.dlq";
    public static final String STUDY_REPORT_DEAD_LETTER_ROUTING_KEY = "lexiflow.study-report.dead";

    public static final String WORD_QA_EXCHANGE = "lexiflow.word-qa.exchange";
    public static final String WORD_QA_QUEUE = "lexiflow.word-qa.queue";
    public static final String WORD_QA_ROUTING_KEY = "lexiflow.word-qa.task";
    public static final String WORD_QA_DEAD_LETTER_EXCHANGE = "lexiflow.word-qa.dlx";
    public static final String WORD_QA_DEAD_LETTER_QUEUE = "lexiflow.word-qa.dlq";
    public static final String WORD_QA_DEAD_LETTER_ROUTING_KEY = "lexiflow.word-qa.dead";

    public static final String CLOZE_REVIEW_EXCHANGE = "lexiflow.cloze-review.exchange";
    public static final String CLOZE_REVIEW_QUEUE = "lexiflow.cloze-review.queue";
    public static final String CLOZE_REVIEW_ROUTING_KEY = "lexiflow.cloze-review.task";
    public static final String CLOZE_REVIEW_DEAD_LETTER_EXCHANGE = "lexiflow.cloze-review.dlx";
    public static final String CLOZE_REVIEW_DEAD_LETTER_QUEUE = "lexiflow.cloze-review.dlq";
    public static final String CLOZE_REVIEW_DEAD_LETTER_ROUTING_KEY = "lexiflow.cloze-review.dead";

    private RabbitMqNames() {
    }
}
