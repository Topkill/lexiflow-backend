package com.lexiflow.infra.rabbitmq;

public final class RabbitMqNames {

    public static final String WORD_IMPORT_EXCHANGE = "lexiflow.word-import.exchange";
    public static final String WORD_IMPORT_QUEUE = "lexiflow.word-import.queue";
    public static final String WORD_IMPORT_ROUTING_KEY = "lexiflow.word-import.task";
    public static final String WORD_IMPORT_DEAD_LETTER_EXCHANGE = "lexiflow.word-import.dlx";
    public static final String WORD_IMPORT_DEAD_LETTER_QUEUE = "lexiflow.word-import.dlq";
    public static final String WORD_IMPORT_DEAD_LETTER_ROUTING_KEY = "lexiflow.word-import.dead";

    private RabbitMqNames() {
    }
}
