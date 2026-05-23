package com.lexiflow.quiz.cloze.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ClozeAttemptAiReviewDisplayFormatterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void formatShouldUseClearMarkdownHierarchyAndNormalizeMixedTextSpacing() throws Exception {
        String json = """
                {
                  "overall": "本次完形填空得分60%，正确完成了第1、2、4空，但第3空和第5空因词性混淆和上下文逻辑判断失误导致错误。整体基础词汇掌握较好，但需要加强对词性和搭配的敏感度。",
                  "mistakeTags": ["词性混淆", "上下文逻辑误判", "近义词辨析不足"],
                  "strengths": [
                    "能正确识别并运用committee、namely等较正式词汇",
                    "对rebuild等动词在重建语境中的使用把握准确"
                  ],
                  "weaknesses": [
                    {
                      "tag": "词性混淆",
                      "blankNos": [3, 5],
                      "comment": "将形容词artistic与动词resume互换，导致第3空缺少形容词修饰director，第5空少动词接续宾语programs。"
                    }
                  ],
                  "suggestions": [
                    "背单词时留意词性和功能。",
                    "针对恢复、继续、重启等概念，对比学习resume、continue、restart等搭配差异。"
                  ],
                  "blankReviews": [
                    {
                      "blankNo": 3,
                      "comment": "你把resume用成了名词，但这里需要形容词修饰director。",
                      "tip": "先看后面的名词，判断前面需要什么词性。"
                    }
                  ]
                }
                """;

        String text = ClozeAttemptAiReviewDisplayFormatter.format(objectMapper.readTree(json));

        assertThat(text).contains("## 总体评价");
        assertThat(text).contains("## 错因标签");
        assertThat(text).contains("## 亮点");
        assertThat(text).contains("## 需要注意");
        assertThat(text).contains("## 学习建议");
        assertThat(text).contains("## 逐空提醒");
        assertThat(text).contains("### 第3空");
        assertThat(text).contains("将形容词 artistic 与动词 resume 互换");
        assertThat(text).contains("形容词修饰 director");
        assertThat(text).doesNotContain("AI评阅");
    }
}
