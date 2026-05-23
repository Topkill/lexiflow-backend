package com.lexiflow.study.task.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.study.task.dto.ChoiceQuestionOptionResponse;
import com.lexiflow.study.task.dto.ChoiceQuestionResponse;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.mapper.WordMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WordChoiceQuestionService {

    private static final int OPTION_COUNT = 4;
    private static final int DISTRACTOR_COUNT = OPTION_COUNT - 1;
    private static final long EXACT_DEFINITION_WEIGHT = 1L << 16;
    private static final long WORD_COMMON_WEIGHT = 1L << 16;
    private static final long STRONG_RELATION_WEIGHT = 1L << 20;
    private static final long SAME_POS_WEIGHT = 1L << 10;
    private static final List<String> TEXT_FIELDS = List.of("cn", "definition", "definitionZh", "zh", "chinese", "meaning");

    private final WordMapper wordMapper;
    private final ObjectMapper objectMapper;

    public ChoiceQuestionResponse buildQuestion(Long taskItemId, Long wordbookId, Word word) {
        return buildQuestion(taskItemId, wordbookId, word, List.of());
    }

    public ChoiceQuestionResponse buildQuestion(Long taskItemId, Long wordbookId, Word word, List<Word> dailyTaskWords) {
        WordChoice target = toChoice(word);
        if (!target.hasDefinition()) {
            return null;
        }

        List<ScoredChoice> scoredChoices = wordMapper.selectChoiceQuestionCandidates(wordbookId).stream()
                .map(this::toChoice)
                .filter(candidate -> canUseCandidate(target, candidate))
                .map(candidate -> new ScoredChoice(candidate, score(target, candidate)))
                .sorted(Comparator.comparingLong(ScoredChoice::score).reversed()
                        .thenComparing(scored -> scored.choice().word()))
                .toList();

        List<WordChoice> distractors = new ArrayList<>();
        for (ScoredChoice scoredChoice : scoredChoices) {
            if (scoredChoice.score() <= 0 || distractors.size() >= DISTRACTOR_COUNT) {
                break;
            }
            addDistractor(target, distractors, scoredChoice.choice());
        }

        if (distractors.size() < DISTRACTOR_COUNT) {
            addRandomWordDistractors(target, distractors, dailyTaskWords, taskItemId, target.wordId(), "daily-task-fallback");
        }

        if (distractors.size() < DISTRACTOR_COUNT) {
            List<WordChoice> fallbackChoices = scoredChoices.stream()
                    .map(ScoredChoice::choice)
                    .sorted(Comparator.comparing(WordChoice::word).thenComparing(WordChoice::wordId))
                    .collect(Collectors.toCollection(ArrayList::new));
            addRandomChoiceDistractors(target, distractors, fallbackChoices, taskItemId, target.wordId(), "wordbook-fallback");
        }

        List<WordChoice> options = new ArrayList<>(distractors);
        options.add(target);
        Collections.shuffle(options, new Random(seed(taskItemId, target.wordId(), "options")));

        List<ChoiceQuestionOptionResponse> responses = options.stream()
                .map(choice -> toOptionResponse(target, choice))
                .toList();
        int correctIndex = options.indexOf(target);
        return new ChoiceQuestionResponse(correctIndex, responses);
    }

    private ChoiceQuestionOptionResponse toOptionResponse(WordChoice target, WordChoice choice) {
        if (Objects.equals(target.wordId(), choice.wordId())) {
            return new ChoiceQuestionOptionResponse(String.valueOf(choice.wordId()), choice.primaryPos(), choice.definition());
        }
        Definition matchedDefinition = mostRelevantDefinition(target, choice);
        String displayDefinition = matchedDefinition == null ? choice.definition() : matchedDefinition.text();
        String displayPos = matchedDefinition == null || !StringUtils.hasText(matchedDefinition.pos())
                ? choice.primaryPos()
                : matchedDefinition.pos();
        return new ChoiceQuestionOptionResponse(String.valueOf(choice.wordId()), displayPos, displayDefinition);
    }

    private Definition mostRelevantDefinition(WordChoice target, WordChoice choice) {
        return choice.definitions().stream()
                .max(Comparator.comparingLong(definition -> definitionRelevance(target, definition)))
                .orElse(null);
    }

    private long definitionRelevance(WordChoice target, Definition definition) {
        long score = 0;
        for (Definition targetDefinition : target.definitions()) {
            if (normalizeForCompare(targetDefinition.text()).equals(normalizeForCompare(definition.text()))) {
                score += Math.max(1, targetDefinition.frequency()) * Math.max(1, definition.frequency()) * EXACT_DEFINITION_WEIGHT;
            }
            score += commonScore(targetDefinition.text(), definition.text());
            if (StringUtils.hasText(targetDefinition.pos()) && targetDefinition.pos().equals(definition.pos())) {
                score += SAME_POS_WEIGHT;
            }
        }
        return score;
    }

    private void addRandomWordDistractors(
            WordChoice target,
            List<WordChoice> distractors,
            List<Word> words,
            Long taskItemId,
            Long wordId,
            String salt
    ) {
        if (words == null || words.isEmpty()) {
            return;
        }
        List<WordChoice> fallbackChoices = words.stream()
                .filter(Objects::nonNull)
                .map(this::toChoice)
                .filter(candidate -> canUseCandidate(target, candidate))
                .sorted(Comparator.comparing(WordChoice::word).thenComparing(WordChoice::wordId))
                .collect(Collectors.toCollection(ArrayList::new));
        addRandomChoiceDistractors(target, distractors, fallbackChoices, taskItemId, wordId, salt);
    }

    private void addRandomChoiceDistractors(
            WordChoice target,
            List<WordChoice> distractors,
            List<WordChoice> fallbackChoices,
            Long taskItemId,
            Long wordId,
            String salt
    ) {
        Collections.shuffle(fallbackChoices, new Random(seed(taskItemId, wordId, salt)));
        for (WordChoice choice : fallbackChoices) {
            if (distractors.size() >= DISTRACTOR_COUNT) {
                break;
            }
            addDistractor(target, distractors, choice);
        }
    }

    private boolean addDistractor(WordChoice target, List<WordChoice> distractors, WordChoice candidate) {
        if (!canUseCandidate(target, candidate)) {
            return false;
        }
        boolean duplicate = distractors.stream().anyMatch(selected -> sameChoice(selected, candidate));
        if (duplicate) {
            return false;
        }
        distractors.add(candidate);
        return true;
    }

    private boolean canUseCandidate(WordChoice target, WordChoice candidate) {
        return candidate.hasDefinition() && !sameChoice(target, candidate);
    }

    private boolean sameChoice(WordChoice first, WordChoice second) {
        return Objects.equals(first.wordId(), second.wordId())
                || sameWord(first, second)
                || sameDefinitions(first, second);
    }

    private long score(WordChoice target, WordChoice candidate) {
        long score = 0;
        for (Definition targetDefinition : target.definitions()) {
            for (Definition candidateDefinition : candidate.definitions()) {
                if (normalizeForCompare(targetDefinition.text()).equals(normalizeForCompare(candidateDefinition.text()))) {
                    score += Math.max(1, targetDefinition.frequency()) * Math.max(1, candidateDefinition.frequency()) * EXACT_DEFINITION_WEIGHT;
                }
            }
        }
        score += commonScore(target.definitionText(), candidate.definitionText());
        score += commonScore(target.word(), candidate.word()) * WORD_COMMON_WEIGHT;
        if (StringUtils.hasText(target.word()) && StringUtils.hasText(candidate.word())
                && (target.word().contains(candidate.word()) || candidate.word().contains(target.word()))) {
            score += STRONG_RELATION_WEIGHT;
        }
        if (target.relWords().contains(candidate.word()) || candidate.relWords().contains(target.word())) {
            score += STRONG_RELATION_WEIGHT;
        }
        if (StringUtils.hasText(target.primaryPos()) && target.primaryPos().equals(candidate.primaryPos())) {
            score += SAME_POS_WEIGHT;
        }
        return score;
    }

    private WordChoice toChoice(Word word) {
        List<Definition> definitions = parseDefinitions(word.getTrans());
        String fallbackDefinition = normalizeText(word.getPrimaryDefinition());
        if (definitions.isEmpty() && StringUtils.hasText(fallbackDefinition)) {
            definitions = List.of(new Definition(normalizeText(word.getPrimaryPos()), fallbackDefinition, 0));
        }
        String definition = StringUtils.hasText(fallbackDefinition)
                ? fallbackDefinition
                : definitions.stream().findFirst().map(Definition::text).orElse("");
        return new WordChoice(
                word.getId(),
                normalizeForCompare(word.getWord()),
                normalizeText(word.getPrimaryPos()),
                definition,
                definitions,
                definitions.stream().map(Definition::text).collect(Collectors.joining("")),
                definitionKey(definitions),
                parseRelatedWords(word.getRelWords())
        );
    }

    private List<Definition> parseDefinitions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, Definition> definitions = new LinkedHashMap<>();
            collectDefinitions(root, 0, definitions);
            return List.copyOf(definitions.values());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void collectDefinitions(JsonNode node, int inheritedFrequency, Map<String, Definition> definitions) {
        collectDefinitions(node, "", inheritedFrequency, definitions);
    }

    private void collectDefinitions(JsonNode node, String inheritedPos, int inheritedFrequency, Map<String, Definition> definitions) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectDefinitions(item, inheritedPos, inheritedFrequency, definitions));
            return;
        }
        if (node.isObject()) {
            int frequency = readFrequency(node, inheritedFrequency);
            String pos = StringUtils.hasText(normalizeText(node.path("pos").asText("")))
                    ? normalizeText(node.path("pos").asText(""))
                    : inheritedPos;
            for (String field : TEXT_FIELDS) {
                collectTextValues(node.path(field)).forEach(text -> putDefinition(definitions, pos, text, frequency));
            }
            collectTextValues(node.path("definitions")).forEach(text -> putDefinition(definitions, pos, text, frequency));
            if (node.has("trans")) {
                collectDefinitions(node.path("trans"), pos, frequency, definitions);
            }
            return;
        }
        putDefinition(definitions, inheritedPos, node.asText(), inheritedFrequency);
    }

    private List<String> collectTextValues(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.addAll(collectTextValues(item)));
            return values;
        }
        if (node.isObject()) {
            return TEXT_FIELDS.stream()
                    .flatMap(field -> collectTextValues(node.path(field)).stream())
                    .toList();
        }
        String text = normalizeText(node.asText());
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private void putDefinition(Map<String, Definition> definitions, String pos, String value, int frequency) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        String key = normalizeForCompare(text);
        Definition existing = definitions.get(key);
        if (existing == null || frequency > existing.frequency()) {
            definitions.put(key, new Definition(normalizeText(pos), text, frequency));
        }
    }

    private int readFrequency(JsonNode node, int fallback) {
        JsonNode frequency = node.path("frequency");
        if (frequency.isNumber()) {
            return Math.max(0, frequency.asInt());
        }
        if (frequency.isTextual()) {
            try {
                return Math.max(0, Integer.parseInt(frequency.asText().trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Set<String> parseRelatedWords(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Set<String> words = new LinkedHashSet<>();
            JsonNode rels = root.path("rels");
            if (rels.isArray()) {
                rels.forEach(rel -> {
                    JsonNode relWords = rel.path("words");
                    if (relWords.isArray()) {
                        relWords.forEach(word -> {
                            String text = normalizeForCompare(word.path("c").asText(""));
                            if (StringUtils.hasText(text)) {
                                words.add(text);
                            }
                        });
                    }
                });
            }
            return words;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private boolean sameWord(WordChoice target, WordChoice candidate) {
        return StringUtils.hasText(target.word()) && target.word().equals(candidate.word());
    }

    private boolean sameDefinitions(WordChoice target, WordChoice candidate) {
        String targetDefinition = normalizeForCompare(target.definition());
        String candidateDefinition = normalizeForCompare(candidate.definition());
        if (StringUtils.hasText(targetDefinition) && targetDefinition.equals(candidateDefinition)) {
            return true;
        }
        return StringUtils.hasText(target.definitionKey()) && target.definitionKey().equals(candidate.definitionKey());
    }

    private String definitionKey(List<Definition> definitions) {
        return definitions.stream()
                .map(definition -> normalizeForCompare(definition.text()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("|"));
    }

    private long commonScore(String first, String second) {
        Set<Integer> firstChars = codePoints(first);
        Set<Integer> secondChars = codePoints(second);
        long score = 0;
        for (Integer value : firstChars) {
            score += secondChars.contains(value) ? 4 : -1;
        }
        for (Integer value : secondChars) {
            score += firstChars.contains(value) ? 4 : -1;
        }
        return score;
    }

    private Set<Integer> codePoints(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return value.toLowerCase(Locale.ROOT)
                .codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeForCompare(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private long seed(Long taskItemId, Long wordId, String salt) {
        long seed = 1_125_899_906_842_597L;
        seed = seed * 31 + (taskItemId == null ? 0 : taskItemId);
        seed = seed * 31 + (wordId == null ? 0 : wordId);
        for (int index = 0; index < salt.length(); index += 1) {
            seed = seed * 31 + salt.charAt(index);
        }
        return seed;
    }

    private record Definition(String pos, String text, int frequency) {
    }

    private record WordChoice(
            Long wordId,
            String word,
            String primaryPos,
            String definition,
            List<Definition> definitions,
            String definitionText,
            String definitionKey,
            Set<String> relWords
    ) {
        private boolean hasDefinition() {
            return wordId != null && StringUtils.hasText(definition);
        }
    }

    private record ScoredChoice(WordChoice choice, long score) {
    }
}
