package com.lexiflow.infra.rabbitmq;

/**
 * RabbitMQ 常量定义类。
 * <p>
 * 定义所有业务队列的交换机名称、队列名称和路由键常量。
 * 每个业务队列均配置对应的死信交换机和死信队列。
 * </p>
 */
public final class RabbitMqNames {

    // ==================== 单词导入 ====================

    /** 单词导入任务交换机名称。 */
    public static final String WORD_IMPORT_EXCHANGE = "lexiflow.word-import.exchange";
    public static final String WORD_IMPORT_QUEUE = "lexiflow.word-import.queue";
    public static final String WORD_IMPORT_ROUTING_KEY = "lexiflow.word-import.task";
    public static final String WORD_IMPORT_DEAD_LETTER_EXCHANGE = "lexiflow.word-import.dlx";
    public static final String WORD_IMPORT_DEAD_LETTER_QUEUE = "lexiflow.word-import.dlq";
    public static final String WORD_IMPORT_DEAD_LETTER_ROUTING_KEY = "lexiflow.word-import.dead";

    // ==================== 完形填空生成 ====================

    /** 完形填空生成任务交换机名称。 */
    public static final String CLOZE_GENERATION_EXCHANGE = "lexiflow.cloze-generation.exchange";
    public static final String CLOZE_GENERATION_QUEUE = "lexiflow.cloze-generation.queue";
    public static final String CLOZE_GENERATION_ROUTING_KEY = "lexiflow.cloze-generation.task";
    public static final String CLOZE_GENERATION_DEAD_LETTER_EXCHANGE = "lexiflow.cloze-generation.dlx";
    public static final String CLOZE_GENERATION_DEAD_LETTER_QUEUE = "lexiflow.cloze-generation.dlq";
    public static final String CLOZE_GENERATION_DEAD_LETTER_ROUTING_KEY = "lexiflow.cloze-generation.dead";

    // ==================== 学习报告 ====================

    /** 学习报告任务交换机名称。 */
    public static final String STUDY_REPORT_EXCHANGE = "lexiflow.study-report.exchange";
    public static final String STUDY_REPORT_QUEUE = "lexiflow.study-report.queue";
    public static final String STUDY_REPORT_ROUTING_KEY = "lexiflow.study-report.task";
    public static final String STUDY_REPORT_DEAD_LETTER_EXCHANGE = "lexiflow.study-report.dlx";
    public static final String STUDY_REPORT_DEAD_LETTER_QUEUE = "lexiflow.study-report.dlq";
    public static final String STUDY_REPORT_DEAD_LETTER_ROUTING_KEY = "lexiflow.study-report.dead";

    // ==================== 单词 QA ====================

    /** 单词 QA 任务交换机名称。 */
    public static final String WORD_QA_EXCHANGE = "lexiflow.word-qa.exchange";
    public static final String WORD_QA_QUEUE = "lexiflow.word-qa.queue";
    public static final String WORD_QA_ROUTING_KEY = "lexiflow.word-qa.task";
    public static final String WORD_QA_DEAD_LETTER_EXCHANGE = "lexiflow.word-qa.dlx";
    public static final String WORD_QA_DEAD_LETTER_QUEUE = "lexiflow.word-qa.dlq";
    public static final String WORD_QA_DEAD_LETTER_ROUTING_KEY = "lexiflow.word-qa.dead";

    // ==================== 完形填空评审 ====================

    /** 完形填空评审任务交换机名称。 */
    public static final String CLOZE_REVIEW_EXCHANGE = "lexiflow.cloze-review.exchange";
    public static final String CLOZE_REVIEW_QUEUE = "lexiflow.cloze-review.queue";
    public static final String CLOZE_REVIEW_ROUTING_KEY = "lexiflow.cloze-review.task";
    public static final String CLOZE_REVIEW_DEAD_LETTER_EXCHANGE = "lexiflow.cloze-review.dlx";
    public static final String CLOZE_REVIEW_DEAD_LETTER_QUEUE = "lexiflow.cloze-review.dlq";
    public static final String CLOZE_REVIEW_DEAD_LETTER_ROUTING_KEY = "lexiflow.cloze-review.dead";

    private RabbitMqNames() {
    }
}
