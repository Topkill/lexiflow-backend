package com.lexiflow.ai.prompt.service;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 内置 AI 提示词注册表
 * <p>
 * 管理系统所有内置的 AI 提示词定义，在初始化时注册各功能类型（单词问答、完形填空、评阅）的
 * 默认系统提示词、规则提示词和输出 Schema。支持按功能类型查询和列表获取。
 * </p>
 */
@Component
public class DefaultAiPromptRegistry {

    private static final String WORD_QA_SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语学习助手。请只输出合法 JSON，不要输出 JSON 外的 Markdown、解释性前后缀或代码块。answer 和 grammarTip 字段的字符串内容可以使用 Markdown 语法。内容面向备考大学生，中文为主，简洁、准确、适合背单词。";
    private static final String WORD_QA_INSTRUCTION_PROMPT = String.join("\n",
            "请基于后端提供的单词上下文 JSON 回答用户问题。",
            "输入 JSON 字段含义：question 是用户提出的问题；targetExam 是用户目标考试；word 是当前单词；trans 是本地词库中的原始释义，优先作为含义依据。",
            "输出 JSON 字段含义：answer 是直接回答用户问题的正文，可以少量使用 Markdown；keyPoints 是 2-4 条短要点，用普通文本概括最值得记的规则、搭配或辨析；relatedWords 是与当前单词确实相关、能帮助辨析或记忆的单词、短语、缩写或表达，每项建议写成“表达 (中文含义/关系)”；followUps 是 2-3 个适合继续追问的中文问题；grammarTip 是最后补充的一条零基础英语语法小知识，优先选择与当前单词、用户问题或回答内容相关的语法点，没有明显关联时由你自由生成一条适合零基础用户的英语语法知识。",
            "回答必须直接回应 question，不能编造未给出的固定知识；如果问题超出单词学习范围，请简短说明并拉回该单词。不要在 JSON 对象外输出 Markdown。answer 和 grammarTip 可以适当使用加粗、反引号、列表和换行，但不要使用 Markdown 引用块，不要给中文翻译添加破折号、引号等额外前缀，不要过度加粗普通中文释义；keyPoints、relatedWords、followUps 不要使用 Markdown 标记。",
            "可以结合你的英语知识补充用法、例句、近反义辨析和备考提示，但不要违背 trans 中给出的核心释义。"
    );

    private static final String CLOZE_QUIZ_SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语测验出题助手。请只输出合法 JSON，不要输出 Markdown、解释性前后缀或代码块。题目面向备考大学生，短文自然连贯；后端程序会自动挖空、生成候选词和判分。";
    private static final String CLOZE_QUIZ_INSTRUCTION_PROMPT = "严格规则：1. passage 必须包含 blankWords 中每个 word 的原词或常见词形变化，explanations 里的 usedForm 必须是 passage 中实际出现的完整词形；2. explanations 必须为每个 blankWords 中的 word 返回一项，word 写原词，usedForm 写 passage 中实际出现的完整词形，例如 word 为 resemble 且文中使用 resembles，则 usedForm 写 resembles；3. 只能依据后端提供的主词性、主释义、全部词性和全部释义选择最合适的义项，不要自造未给出的词义；4. passage 不得出现中文释义、英文释义、词性解释、because it relates to 或类似泄题模板；5. 不要输出 ___1___ 这类占位符，后端会按实际出现位置自动挖空并生成正确答案；6. backgroundWords 是软约束，尽量自然融入 passage，影响通顺时可以省略；7. 不要输出 candidateWords 或 blanks，候选词和答案由后端程序生成；8. passage 要自然连贯，控制在 200-250 个英文词，并且必须符合英语语法，不能有语法错误；9. 所有 JSON 字段值都不要使用 Markdown 标记，例如 **word**、__word__ 或 `word`；10. passageZh 要翻译整篇短文；11. explanations 里的 usedPos 和 definitionZh 必须对应你在 passage 里真正采用的那个义项，reasonZh 要简短解释为什么这里选这个词，且必须是中文；12. 若某个词有多个义项，优先选择最符合上下文且最自然的那个。";

    private static final String CLOZE_REVIEW_SYSTEM_PROMPT = "你是 LexiFlow 的 AI 英语完形填空评阅助手。请只输出合法 JSON，不要输出 Markdown 代码块。评阅要简洁、具体、鼓励但不空泛，面向备考大学生。";
    private static final String CLOZE_REVIEW_INSTRUCTION_PROMPT = "全部用中文，不要输出 Markdown 代码块。\n要求：1. overall 先给本次整体评价，简洁但具体；2. mistakeTags 只保留 2-4 个最主要的错因标签；3. strengths 写 2-3 条亮点；4. weaknesses 只总结影响较大的问题，并标出相关空格序号；5. suggestions 写 2-4 条可执行的学习建议；6. blankReviews 只写答错的空，分别说明错在哪里和下次怎么想；7. grammarTip 最后补充一条零基础英语语法小知识，优先选择与本次错因、空格词性、句法结构或评阅建议相关的语法点，没有明显关联时由你自由生成一条适合零基础用户的英语语法知识，允许使用少量 Markdown 排版；8. 只能依据后端提供的原英文短文、作答结果、本题采用词性、本题采用中文释义、后端已有中文选择原因，以及错题的全部词性释义来分析，不要编造题目外信息；9. 不要提用户 ID、题目标题或中文翻译；10. 关键结论可以用 **加粗**；11. 允许使用 Markdown 语法适当排版，关键结论和英文词可以组合写成 **`resume`** 这种嵌套强调；12. 英文词可以用 `resume` 这种反引号包裹；13. 语言自然，适合备考大学生。";

    private final Map<AiPromptFeatureType, DefaultAiPromptDefinition> definitions;

    public DefaultAiPromptRegistry(AiPromptOutputSchemaService outputSchemaService) {
        definitions = new EnumMap<>(AiPromptFeatureType.class);
        register(new DefaultAiPromptDefinition(AiPromptFeatureType.WORD_QA, "builtin:WORD_QA", "默认 AI 问答提示词", WORD_QA_SYSTEM_PROMPT, WORD_QA_INSTRUCTION_PROMPT, outputSchemaService.defaultSchemaJson(AiPromptFeatureType.WORD_QA)));
        register(new DefaultAiPromptDefinition(AiPromptFeatureType.CLOZE_QUIZ, "builtin:CLOZE_QUIZ", "默认 AI 完形填空提示词", CLOZE_QUIZ_SYSTEM_PROMPT, CLOZE_QUIZ_INSTRUCTION_PROMPT, outputSchemaService.defaultSchemaJson(AiPromptFeatureType.CLOZE_QUIZ)));
        register(new DefaultAiPromptDefinition(AiPromptFeatureType.CLOZE_REVIEW, "builtin:CLOZE_REVIEW", "默认 AI 评阅提示词", CLOZE_REVIEW_SYSTEM_PROMPT, CLOZE_REVIEW_INSTRUCTION_PROMPT, outputSchemaService.defaultSchemaJson(AiPromptFeatureType.CLOZE_REVIEW)));
    }

    /**
     * 获取指定功能类型的内置提示词定义
     *
     * @param featureType AI 功能类型
     * @return 内置提示词定义，不存在时返回 null
     */
    public DefaultAiPromptDefinition get(AiPromptFeatureType featureType) {
        return definitions.get(featureType);
    }

    /**
     * 获取所有内置提示词定义列表
     *
     * @return 所有内置提示词定义列表
     */
    public List<DefaultAiPromptDefinition> list() {
        return Arrays.stream(AiPromptFeatureType.values())
                .map(definitions::get)
                .toList();
    }

    private void register(DefaultAiPromptDefinition definition) {
        definitions.put(definition.featureType(), definition);
    }
}
