package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DefaultAiPromptRegistry {

    private static final String WORD_QA_SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语学习助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。内容面向备考大学生，中文为主，简洁、准确、适合背单词。";
    private static final String WORD_QA_INSTRUCTION_PROMPT = "输出 JSON 字段：answer 字符串；keyPoints 字符串数组；relatedWords 字符串数组；followUps 字符串数组。回答必须直接回应用户问题，不能编造未给出的固定知识；如果问题超出单词学习范围，请简短说明并拉回该单词。\n请基于以下单词上下文生成内容，避免编造不存在的固定搭配。";

    private static final String CLOZE_QUIZ_SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语测验出题助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。题目面向备考大学生，短文自然连贯；后端程序会自动挖空、生成候选词和判分。";
    private static final String CLOZE_QUIZ_INSTRUCTION_PROMPT = "输出 JSON 对象：title 字符串；passage 字符串，必须是包含 blankWords 原词的完整英文短文，不要提前挖空；passageZh 字符串，短文中文翻译；explanations 数组，每项包含 word、usedPos、definitionZh、reasonZh；usedPos 字符串，definitionZh 字符串，reasonZh 字符串，且 reasonZh 必须是中文。\n严格规则：1. passage 必须逐字包含 blankWords 中每个 word，且每个 word 在 passage 中只出现一次；2. 只能依据后端提供的主词性、主释义、全部词性和全部释义选择最合适的义项，不要自造未给出的词义；3. passage 不得出现中文释义、英文释义、词性解释、because it relates to 或类似泄题模板；4. 不要输出 ___1___ 这类占位符，后端会按实际出现位置自动挖空并生成正确答案；5. backgroundWords 是软约束，尽量自然融入 passage，影响通顺时可以省略；6. 不要输出 candidateWords 或 blanks，候选词和答案由后端程序生成；7. passage 要自然连贯，控制在 100-180 个英文词，并且必须符合英语语法，不能有语法错误；8. passageZh 要翻译整篇短文；9. explanations 里的 usedPos 和 definitionZh 必须对应你在 passage 里真正采用的那个义项，reasonZh 要简短解释为什么这里选这个词；10. 若某个词有多个义项，优先选择最符合上下文且最自然的那个。";

    private static final String CLOZE_REVIEW_SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语完形填空评阅助手。请只输出合法 JSON，不要输出 Markdown 代码块。评阅要简洁、具体、鼓励但不空泛，面向备考大学生。";
    private static final String CLOZE_REVIEW_INSTRUCTION_PROMPT = "输出 JSON 对象：overall 字符串；mistakeTags 字符串数组；strengths 字符串数组；weaknesses 数组，每项包含 tag、blankNos、comment；suggestions 字符串数组；blankReviews 数组，每项包含 blankNo、comment、tip。全部用中文，不要输出 Markdown 代码块。\n要求：1. overall 先给本次整体评价，简洁但具体；2. mistakeTags 只保留 2-4 个最主要的错因标签；3. strengths 写 2-3 条亮点；4. weaknesses 只总结影响较大的问题，并标出相关空格序号；5. suggestions 写 2-4 条可执行的学习建议；6. blankReviews 只写答错的空，分别说明错在哪里和下次怎么想；7. 只能依据后端提供的原英文短文、作答结果、本题采用词性、本题采用中文释义、后端已有中文选择原因，以及错题的全部词性释义来分析，不要编造题目外信息；8. 不要提用户 ID、题目标题或中文翻译；9. 关键结论可以用 **加粗**；10. 允许使用 Markdown 语法适当排版，关键结论和英文词可以组合写成 **`resume`** 这种嵌套强调；11. 英文词可以用 `resume` 这种反引号包裹；12. 语言自然，适合备考大学生。";

    private final Map<AiPromptFeatureType, DefaultAiPromptDefinition> definitions;

    public DefaultAiPromptRegistry() {
        definitions = new EnumMap<>(AiPromptFeatureType.class);
        register(new DefaultAiPromptDefinition(AiPromptFeatureType.WORD_QA, "builtin:WORD_QA", "默认 AI 问答提示词", WORD_QA_SYSTEM_PROMPT, WORD_QA_INSTRUCTION_PROMPT));
        register(new DefaultAiPromptDefinition(AiPromptFeatureType.CLOZE_QUIZ, "builtin:CLOZE_QUIZ", "默认 AI 完形填空提示词", CLOZE_QUIZ_SYSTEM_PROMPT, CLOZE_QUIZ_INSTRUCTION_PROMPT));
        register(new DefaultAiPromptDefinition(AiPromptFeatureType.CLOZE_REVIEW, "builtin:CLOZE_REVIEW", "默认 AI 评阅提示词", CLOZE_REVIEW_SYSTEM_PROMPT, CLOZE_REVIEW_INSTRUCTION_PROMPT));
    }

    public DefaultAiPromptDefinition get(AiPromptFeatureType featureType) {
        return definitions.get(featureType);
    }

    public List<DefaultAiPromptDefinition> list() {
        return Arrays.stream(AiPromptFeatureType.values())
                .map(definitions::get)
                .toList();
    }

    private void register(DefaultAiPromptDefinition definition) {
        definitions.put(definition.featureType(), definition);
    }
}
