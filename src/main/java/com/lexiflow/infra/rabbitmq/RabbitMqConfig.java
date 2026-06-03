package com.lexiflow.infra.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange wordImportExchange() {
        return new DirectExchange(RabbitMqNames.WORD_IMPORT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange wordImportDeadLetterExchange() {
        return new DirectExchange(RabbitMqNames.WORD_IMPORT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange clozeGenerationExchange() {
        return new DirectExchange(RabbitMqNames.CLOZE_GENERATION_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange clozeGenerationDeadLetterExchange() {
        return new DirectExchange(RabbitMqNames.CLOZE_GENERATION_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange studyReportExchange() {
        return new DirectExchange(RabbitMqNames.STUDY_REPORT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange studyReportDeadLetterExchange() {
        return new DirectExchange(RabbitMqNames.STUDY_REPORT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange wordQaExchange() {
        return new DirectExchange(RabbitMqNames.WORD_QA_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange wordQaDeadLetterExchange() {
        return new DirectExchange(RabbitMqNames.WORD_QA_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange clozeReviewExchange() {
        return new DirectExchange(RabbitMqNames.CLOZE_REVIEW_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange clozeReviewDeadLetterExchange() {
        return new DirectExchange(RabbitMqNames.CLOZE_REVIEW_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue wordImportQueue() {
        return QueueBuilder.durable(RabbitMqNames.WORD_IMPORT_QUEUE)
                .deadLetterExchange(RabbitMqNames.WORD_IMPORT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.WORD_IMPORT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue wordImportDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqNames.WORD_IMPORT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue clozeGenerationQueue() {
        return QueueBuilder.durable(RabbitMqNames.CLOZE_GENERATION_QUEUE)
                .deadLetterExchange(RabbitMqNames.CLOZE_GENERATION_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.CLOZE_GENERATION_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue clozeGenerationDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqNames.CLOZE_GENERATION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue studyReportQueue() {
        return QueueBuilder.durable(RabbitMqNames.STUDY_REPORT_QUEUE)
                .deadLetterExchange(RabbitMqNames.STUDY_REPORT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.STUDY_REPORT_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue studyReportDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqNames.STUDY_REPORT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue wordQaQueue() {
        return QueueBuilder.durable(RabbitMqNames.WORD_QA_QUEUE)
                .deadLetterExchange(RabbitMqNames.WORD_QA_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.WORD_QA_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue wordQaDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqNames.WORD_QA_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue clozeReviewQueue() {
        return QueueBuilder.durable(RabbitMqNames.CLOZE_REVIEW_QUEUE)
                .deadLetterExchange(RabbitMqNames.CLOZE_REVIEW_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqNames.CLOZE_REVIEW_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue clozeReviewDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqNames.CLOZE_REVIEW_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding wordImportBinding(
            @Qualifier("wordImportQueue") Queue wordImportQueue,
            @Qualifier("wordImportExchange") DirectExchange wordImportExchange
    ) {
        return BindingBuilder.bind(wordImportQueue)
                .to(wordImportExchange)
                .with(RabbitMqNames.WORD_IMPORT_ROUTING_KEY);
    }

    @Bean
    public Binding wordImportDeadLetterBinding(
            @Qualifier("wordImportDeadLetterQueue") Queue wordImportDeadLetterQueue,
            @Qualifier("wordImportDeadLetterExchange") DirectExchange wordImportDeadLetterExchange
    ) {
        return BindingBuilder.bind(wordImportDeadLetterQueue)
                .to(wordImportDeadLetterExchange)
                .with(RabbitMqNames.WORD_IMPORT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Binding clozeGenerationBinding(
            @Qualifier("clozeGenerationQueue") Queue clozeGenerationQueue,
            @Qualifier("clozeGenerationExchange") DirectExchange clozeGenerationExchange
    ) {
        return BindingBuilder.bind(clozeGenerationQueue)
                .to(clozeGenerationExchange)
                .with(RabbitMqNames.CLOZE_GENERATION_ROUTING_KEY);
    }

    @Bean
    public Binding clozeGenerationDeadLetterBinding(
            @Qualifier("clozeGenerationDeadLetterQueue") Queue clozeGenerationDeadLetterQueue,
            @Qualifier("clozeGenerationDeadLetterExchange") DirectExchange clozeGenerationDeadLetterExchange
    ) {
        return BindingBuilder.bind(clozeGenerationDeadLetterQueue)
                .to(clozeGenerationDeadLetterExchange)
                .with(RabbitMqNames.CLOZE_GENERATION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Binding studyReportBinding(
            @Qualifier("studyReportQueue") Queue studyReportQueue,
            @Qualifier("studyReportExchange") DirectExchange studyReportExchange
    ) {
        return BindingBuilder.bind(studyReportQueue)
                .to(studyReportExchange)
                .with(RabbitMqNames.STUDY_REPORT_ROUTING_KEY);
    }

    @Bean
    public Binding studyReportDeadLetterBinding(
            @Qualifier("studyReportDeadLetterQueue") Queue studyReportDeadLetterQueue,
            @Qualifier("studyReportDeadLetterExchange") DirectExchange studyReportDeadLetterExchange
    ) {
        return BindingBuilder.bind(studyReportDeadLetterQueue)
                .to(studyReportDeadLetterExchange)
                .with(RabbitMqNames.STUDY_REPORT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Binding wordQaBinding(
            @Qualifier("wordQaQueue") Queue wordQaQueue,
            @Qualifier("wordQaExchange") DirectExchange wordQaExchange
    ) {
        return BindingBuilder.bind(wordQaQueue)
                .to(wordQaExchange)
                .with(RabbitMqNames.WORD_QA_ROUTING_KEY);
    }

    @Bean
    public Binding wordQaDeadLetterBinding(
            @Qualifier("wordQaDeadLetterQueue") Queue wordQaDeadLetterQueue,
            @Qualifier("wordQaDeadLetterExchange") DirectExchange wordQaDeadLetterExchange
    ) {
        return BindingBuilder.bind(wordQaDeadLetterQueue)
                .to(wordQaDeadLetterExchange)
                .with(RabbitMqNames.WORD_QA_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Binding clozeReviewBinding(
            @Qualifier("clozeReviewQueue") Queue clozeReviewQueue,
            @Qualifier("clozeReviewExchange") DirectExchange clozeReviewExchange
    ) {
        return BindingBuilder.bind(clozeReviewQueue)
                .to(clozeReviewExchange)
                .with(RabbitMqNames.CLOZE_REVIEW_ROUTING_KEY);
    }

    @Bean
    public Binding clozeReviewDeadLetterBinding(
            @Qualifier("clozeReviewDeadLetterQueue") Queue clozeReviewDeadLetterQueue,
            @Qualifier("clozeReviewDeadLetterExchange") DirectExchange clozeReviewDeadLetterExchange
    ) {
        return BindingBuilder.bind(clozeReviewDeadLetterQueue)
                .to(clozeReviewDeadLetterExchange)
                .with(RabbitMqNames.CLOZE_REVIEW_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
