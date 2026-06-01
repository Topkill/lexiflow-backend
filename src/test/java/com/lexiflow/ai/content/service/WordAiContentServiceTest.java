package com.lexiflow.ai.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.content.domain.WordAiQa;
import com.lexiflow.ai.content.dto.WordAiContentResponse;
import com.lexiflow.ai.content.mapper.WordAiQaMapper;
import com.lexiflow.ai.core.client.AiStreamDeltaHandler;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.service.AiPromptOutputSchemaService;
import com.lexiflow.ai.prompt.service.AiPromptTemplateService;
import com.lexiflow.ai.prompt.service.ResolvedAiPromptTemplate;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.user.domain.TargetExam;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.service.UserService;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.domain.WordbookType;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.service.WordbookService;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class WordAiContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void wordQaSourceJsonShouldOnlyExposeMinimalContext() throws Exception {
        WordAiContentService service = new WordAiContentService(
                mock(WordAiQaMapper.class),
                mock(AsyncTaskService.class),
                mock(AiGatewayService.class),
                mock(WordbookService.class),
                mock(WordMapper.class),
                mock(UserService.class),
                objectMapper,
                mock(AiPromptTemplateService.class),
                mock(AiPromptOutputSchemaService.class),
                transactionTemplate()
        );
        Word word = new Word();
        word.setWord("namely");
        word.setTrans("[{\"pos\":\"adv.\",\"cn\":\"即，也就是\"}]");
        word.setSentences("[{\"c\":\"A district should serve its clientele, namely students.\"}]");
        word.setSynos("[{\"ws\":[\"i.e.\"]}]");
        word.setRelWords("{\"root\":\"keen\"}");
        word.setPrimaryDefinition("即，也就是");
        UserSettings settings = new UserSettings();
        settings.setTargetExam(TargetExam.CET4);

        String sourceJson = ReflectionTestUtils.invokeMethod(
                service,
                "buildSourceJson",
                AiContentType.WORD_QA,
                null,
                word,
                settings,
                "这个词是什么意思？"
        );
        JsonNode source = objectMapper.readTree(sourceJson);

        assertThat(source.fieldNames()).toIterable()
                .containsExactly("question", "targetExam", "word", "trans");
        assertThat(source.path("question").asText()).isEqualTo("这个词是什么意思？");
        assertThat(source.path("targetExam").asText()).isEqualTo("CET4");
        assertThat(source.path("word").asText()).isEqualTo("namely");
        assertThat(source.path("trans").asText()).contains("即，也就是");
        assertThat(source.has("contentType")).isFalse();
        assertThat(source.has("wordbook")).isFalse();
        assertThat(source.has("wordbookScope")).isFalse();
        assertThat(source.toString()).doesNotContain("sentences", "synos", "relWords", "primaryDefinition");
    }

    @Test
    void streamWordQuestionShouldKeepStreamingAfterAnswerNewlines() throws Exception {
        WordAiQaMapper wordAiQaMapper = mock(WordAiQaMapper.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        AiGatewayService gatewayService = mock(AiGatewayService.class);
        WordbookService wordbookService = mock(WordbookService.class);
        WordMapper wordMapper = mock(WordMapper.class);
        UserService userService = mock(UserService.class);
        AiPromptTemplateService promptTemplateService = mock(AiPromptTemplateService.class);
        AiPromptOutputSchemaService outputSchemaService = new AiPromptOutputSchemaService(objectMapper);
        WordAiContentService service = new WordAiContentService(
                wordAiQaMapper,
                asyncTaskService,
                gatewayService,
                wordbookService,
                wordMapper,
                userService,
                objectMapper,
                promptTemplateService,
                outputSchemaService,
                transactionTemplate()
        );
        Wordbook wordbook = new Wordbook();
        wordbook.setId(1L);
        wordbook.setName("CET4");
        wordbook.setType(WordbookType.CET4);
        wordbook.setDifficultyLevel(4);
        Word word = new Word();
        word.setId(67L);
        word.setWordbookId(1L);
        word.setWord("plentiful");
        word.setTrans("[{\"pos\":\"adj.\",\"cn\":\"丰富的，众多的\"}]");
        UserSettings settings = new UserSettings();
        settings.setTargetExam(TargetExam.CET4);
        String schemaJson = wordQaSchemaWithExamples(outputSchemaService);
        ResolvedAiPromptTemplate template = new ResolvedAiPromptTemplate(
                AiPromptFeatureType.WORD_QA,
                1L,
                null,
                "默认 AI 问答提示词",
                "system",
                "instruction",
                schemaJson,
                true,
                "fingerprint"
        );
        String aiJson = """
                {"answer":"**plentiful** 是形容词。\\n\\n例句：\\n- It is plentiful.","keyPoints":["点一","点二"],"relatedWords":["plenty (大量)"],"followUps":["继续问？"],"examples":["例一","例二"]}
                """.trim();

        when(wordbookService.getEnabledWordbook(1L)).thenReturn(wordbook);
        when(wordMapper.selectOne(any())).thenReturn(word);
        when(userService.getOrCreateSettings(9L)).thenReturn(settings);
        when(promptTemplateService.resolve(AiPromptFeatureType.WORD_QA, 1L)).thenReturn(template);
        AsyncTask task = new AsyncTask();
        task.setId(88L);
        when(asyncTaskService.createTask(eq(9L), any(), any())).thenReturn(task);
        when(wordAiQaMapper.insert(any(WordAiQa.class))).thenAnswer(invocation -> {
            WordAiQa qa = invocation.getArgument(0);
            qa.setId(123L);
            return 1;
        });
        when(gatewayService.generateJsonStream(eq(9L), eq(AiContentType.WORD_QA), any(), any(), eq(88L)))
                .thenAnswer(invocation -> {
                    AiStreamDeltaHandler handler = invocation.getArgument(3);
                    for (int index = 0; index < aiJson.length(); index += 5) {
                        handler.onDelta(aiJson.substring(index, Math.min(index + 5, aiJson.length())));
                    }
                    return new AiChatCompletionResult(aiJson, 0, 0, 0);
                });

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service.streamWordQuestion(9L, 1L, 67L, "plentiful 是什么意思？", true, outputStream);
        String response = outputStream.toString(StandardCharsets.UTF_8);

        assertThat(response).contains("event: chunk");
        assertThat(response).contains("event: field_item");
        assertThat(response).contains("\"field\":\"keyPoints\"");
        assertThat(response).contains("\"field\":\"relatedWords\"");
        assertThat(response).contains("\"field\":\"followUps\"");
        assertThat(response).contains("\"field\":\"examples\"");
        assertThat(response).contains("\"outputSchema\"");
        assertThat(response).contains("\"resultId\":\"123\"");
        assertThat(response).contains("event: done");
    }

    @Test
    void generateWordQuestionShouldReuseWordAiQaCacheWithoutCallingAi() {
        WordAiQaMapper wordAiQaMapper = mock(WordAiQaMapper.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        AiGatewayService gatewayService = mock(AiGatewayService.class);
        WordbookService wordbookService = mock(WordbookService.class);
        WordMapper wordMapper = mock(WordMapper.class);
        UserService userService = mock(UserService.class);
        AiPromptTemplateService promptTemplateService = mock(AiPromptTemplateService.class);
        AiPromptOutputSchemaService outputSchemaService = new AiPromptOutputSchemaService(objectMapper);
        WordAiContentService service = new WordAiContentService(
                wordAiQaMapper,
                asyncTaskService,
                gatewayService,
                wordbookService,
                wordMapper,
                userService,
                objectMapper,
                promptTemplateService,
                outputSchemaService,
                transactionTemplate()
        );
        Wordbook wordbook = new Wordbook();
        wordbook.setId(1L);
        wordbook.setName("CET4");
        wordbook.setType(WordbookType.CET4);
        wordbook.setDifficultyLevel(4);
        Word word = new Word();
        word.setId(67L);
        word.setWordbookId(1L);
        word.setWord("plentiful");
        word.setTrans("[{\"pos\":\"adj.\",\"cn\":\"丰富的，众多的\"}]");
        UserSettings settings = new UserSettings();
        settings.setTargetExam(TargetExam.CET4);
        AsyncTask task = new AsyncTask();
        task.setId(88L);
        WordAiQa cached = new WordAiQa();
        cached.setId(99L);
        cached.setHitCount(2);
        cached.setContentJson("{\"answer\":\"缓存回答\",\"keyPoints\":[]}");
        ResolvedAiPromptTemplate template = new ResolvedAiPromptTemplate(
                AiPromptFeatureType.WORD_QA,
                1L,
                null,
                "默认 AI 问答提示词",
                "system",
                "instruction",
                outputSchemaService.defaultSchemaJson(AiPromptFeatureType.WORD_QA),
                true,
                "fingerprint"
        );

        when(wordbookService.getEnabledWordbook(1L)).thenReturn(wordbook);
        when(wordMapper.selectOne(any())).thenReturn(word);
        when(userService.getOrCreateSettings(9L)).thenReturn(settings);
        when(promptTemplateService.resolve(AiPromptFeatureType.WORD_QA, 1L)).thenReturn(template);
        when(asyncTaskService.createTask(eq(9L), any(), any())).thenReturn(task);
        when(wordAiQaMapper.selectOne(any())).thenReturn(cached);

        WordAiContentResponse response = service.generateWordQuestion(9L, 1L, 67L, "plentiful 是什么意思？", false);

        assertThat(response.cacheHit()).isTrue();
        assertThat(response.resultId()).isEqualTo("99");
        assertThat(response.content().path("answer").asText()).isEqualTo("缓存回答");
        assertThat(cached.getHitCount()).isEqualTo(3);
        verify(gatewayService, never()).generateJson(any(), any(), any(), any());
        verify(asyncTaskService).markSuccess(88L, 99L, "AI 问答命中缓存");
    }

    @Test
    void wordQaStreamExtractorShouldEmitAnswerAndArrayItems() throws Exception {
        Class<?> extractorClass = Class.forName(WordAiContentService.class.getName() + "$WordQaJsonStreamExtractor");
        Constructor<?> constructor = extractorClass.getDeclaredConstructor(JsonNode.class, ObjectMapper.class);
        constructor.setAccessible(true);
        AiPromptOutputSchemaService outputSchemaService = new AiPromptOutputSchemaService(objectMapper);
        JsonNode outputSchema = objectMapper.readTree(wordQaSchemaWithExamples(outputSchemaService));
        Object extractor = constructor.newInstance(outputSchema, objectMapper);
        Method append = extractorClass.getDeclaredMethod("append", String.class);
        append.setAccessible(true);

        String json = """
                {"answer":"第一段\\n\\n- 第二段","keyPoints":["点一","点二"],"relatedWords":["namely (即)"],"followUps":["继续问？"],"examples":["例一"]}
                """.trim();
        StringBuilder answer = new StringBuilder();
        List<String> items = new ArrayList<>();
        for (int index = 0; index < json.length(); index += 4) {
            Object delta = append.invoke(extractor, json.substring(index, Math.min(index + 4, json.length())));
            Method answerMethod = delta.getClass().getDeclaredMethod("answer");
            Method itemsMethod = delta.getClass().getDeclaredMethod("items");
            answerMethod.setAccessible(true);
            itemsMethod.setAccessible(true);
            answer.append((String) answerMethod.invoke(delta));
            @SuppressWarnings("unchecked")
            List<Object> fieldItems = (List<Object>) itemsMethod.invoke(delta);
            for (Object item : fieldItems) {
                Method fieldMethod = item.getClass().getDeclaredMethod("field");
                Method itemMethod = item.getClass().getDeclaredMethod("item");
                fieldMethod.setAccessible(true);
                itemMethod.setAccessible(true);
                JsonNode itemNode = (JsonNode) itemMethod.invoke(item);
                items.add(fieldMethod.invoke(item) + ":" + itemNode.asText());
            }
        }

        assertThat(answer.toString()).isEqualTo("第一段\n\n- 第二段");
        assertThat(items).containsExactly(
                "keyPoints:点一",
                "keyPoints:点二",
                "relatedWords:namely (即)",
                "followUps:继续问？",
                "examples:例一"
        );
    }

    private String wordQaSchemaWithExamples(AiPromptOutputSchemaService outputSchemaService) throws Exception {
        ObjectNode schema = (ObjectNode) objectMapper.readTree(outputSchemaService.defaultSchemaJson(AiPromptFeatureType.WORD_QA));
        schema.set("examples", objectMapper.createArrayNode());
        return objectMapper.writeValueAsString(schema);
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
