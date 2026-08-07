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

/**
 * 选择题生成服务。
 * <p>根据当前学习的单词，从词库中智能选取干扰项，生成中文释义四选一选择题。
 * 干扰项选取采用三级回退策略：
 * <ol>
 *   <li>优先从词库中选取释义相近的单词（基于评分排序）</li>
 *   <li>不足时从当前任务组的单词中随机选取</li>
 *   <li>仍不足时从词库候选中随机选取</li>
 * </ol>
 * 选项顺序基于任务项 ID 的确定性随机种子打乱，保证同一任务项的选项顺序稳定。</p>
 */
@Service
@RequiredArgsConstructor
public class WordChoiceQuestionService {

    /** 选项总数 */
    private static final int OPTION_COUNT = 4;
    /** 干扰项数量（选项数 - 1） */
    private static final int DISTRACTOR_COUNT = OPTION_COUNT - 1;
    /** 完全匹配释义的权重 */
    private static final long EXACT_DEFINITION_WEIGHT = 1L << 16;
    /** 单词字符重叠度权重 */
    private static final long WORD_COMMON_WEIGHT = 1L << 16;
    /** 强关联词（包含关系/相关词）权重 */
    private static final long STRONG_RELATION_WEIGHT = 1L << 20;
    /** 相同词性权重 */
    private static final long SAME_POS_WEIGHT = 1L << 10;
    /** 释义 JSON 中用于提取文本的字段名列表 */
    private static final List<String> TEXT_FIELDS = List.of("cn", "definition", "definitionZh", "zh", "chinese", "meaning");

    private final WordMapper wordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构建选择题（不含额外任务词列表的回退）。
     *
     * @param taskItemId 任务项 ID
     * @param wordbookId 词书 ID
     * @param word       当前学习的单词
     * @return 选择题响应，若无有效释义则返回 null
     */
    public ChoiceQuestionResponse buildQuestion(Long taskItemId, Long wordbookId, Word word) {
        return buildQuestion(taskItemId, wordbookId, word, List.of());
    }

    /**
     * 构建中文释义选择题。
     * <p>从词库中选取干扰项，生成 4 个选项的选择题。
     * 干扰项按评分从高到低选取，不足时依次从任务组单词、词库候选中随机补充。</p>
     *
     * @param taskItemId     任务项 ID（用于确定性随机种子）
     * @param wordbookId     词书 ID
     * @param word           当前学习的单词
     * @param dailyTaskWords 当前任务组的单词列表（作为回退干扰项来源）
     * @return 选择题响应，若无有效释义则返回 null
     */
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

    /** 将干扰项单词转换为选项响应，为正确选项显示完整释义，为干扰项显示最相关的释义。 */
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

    /** 从候选词的所有释义中找出与目标词最相关的释义。 */
    private Definition mostRelevantDefinition(WordChoice target, WordChoice choice) {
        return choice.definitions().stream()
                .max(Comparator.comparingLong(definition -> definitionRelevance(target, definition)))
                .orElse(null);
    }

    /** 计算候选释义与目标词释义的相关度评分。 */
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

    /** 从当前任务组的单词中随机选取干扰项（第二级回退）。 */
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

    /** 从候选列表中按确定性随机顺序选取干扰项（通用回退逻辑）。 */
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

    /** 尝试添加一个干扰项，去重并校验可用性。 */
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

    /** 判断候选词是否可作为干扰项（有释义且不与目标词重复）。 */
    private boolean canUseCandidate(WordChoice target, WordChoice candidate) {
        return candidate.hasDefinition() && !sameChoice(target, candidate);
    }

    /** 判断两个选项是否代表同一个词（同 ID、同拼写或同释义）。 */
    private boolean sameChoice(WordChoice first, WordChoice second) {
        return Objects.equals(first.wordId(), second.wordId())
                || sameWord(first, second)
                || sameDefinitions(first, second);
    }

    /**
     * 计算候选词与目标词的干扰度评分。
     * <p>评分维度：释义重叠、字符重叠、单词包含关系、相关词关联、词性一致。</p>
     */
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

    /** 将 Word 实体转换为内部 WordChoice 表示。 */
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

    /** 从释义 JSON 字符串中解析出所有释义条目。 */
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

    /** 递归遍历 JSON 节点，收集所有释义文本到 Map 中（去重，保留高频）。 */
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

    /** 递归遍历 JSON 节点，收集所有文本值（支持数组、对象和叶子节点）。 */
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

    /** 将一条释义放入 Map，相同释义保留频率更高的版本。 */
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

    /** 从 JSON 节点中读取 frequency 字段，支持数字和字符串格式。 */
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

    /** 从相关词 JSON 中解析出所有关联单词。 */
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

    /** 判断两个选项是否为同一个单词（拼写相同）。 */
    private boolean sameWord(WordChoice target, WordChoice candidate) {
        return StringUtils.hasText(target.word()) && target.word().equals(candidate.word());
    }

    /** 判断两个选项的释义是否相同（主释义或释义键相同）。 */
    private boolean sameDefinitions(WordChoice target, WordChoice candidate) {
        String targetDefinition = normalizeForCompare(target.definition());
        String candidateDefinition = normalizeForCompare(candidate.definition());
        if (StringUtils.hasText(targetDefinition) && targetDefinition.equals(candidateDefinition)) {
            return true;
        }
        return StringUtils.hasText(target.definitionKey()) && target.definitionKey().equals(candidate.definitionKey());
    }

    /** 生成释义键，用于去重比较。 */
    private String definitionKey(List<Definition> definitions) {
        return definitions.stream()
                .map(definition -> normalizeForCompare(definition.text()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("|"));
    }

    /** 计算两个字符串的字符重叠度评分（共有字符 +4，独有字符 -1）。 */
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

    /** 提取字符串的所有非空白字符 code point 集合。 */
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

    /** 规范化文本：去除多余空白。 */
    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    /** 规范化文本用于比较：转小写并去除所有空白。 */
    private String normalizeForCompare(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 基于任务项 ID、单词 ID 和盐值生成确定性随机种子。 */
    private long seed(Long taskItemId, Long wordId, String salt) {
        long seed = 1_125_899_906_842_597L;
        seed = seed * 31 + (taskItemId == null ? 0 : taskItemId);
        seed = seed * 31 + (wordId == null ? 0 : wordId);
        for (int index = 0; index < salt.length(); index += 1) {
            seed = seed * 31 + salt.charAt(index);
        }
        return seed;
    }

    /** 释义条目：词性、释义文本、使用频率。 */
    private record Definition(String pos, String text, int frequency) {
    }

    /** 单词选择题内部表示，包含单词信息和所有释义。 */
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

    /** 带评分的候选词。 */
    private record ScoredChoice(WordChoice choice, long score) {
    }
}
