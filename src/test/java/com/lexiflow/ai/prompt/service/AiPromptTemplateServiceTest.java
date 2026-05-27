package com.lexiflow.ai.prompt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.prompt.domain.AiPromptFeatureBinding;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.domain.AiPromptTemplate;
import com.lexiflow.ai.prompt.dto.AiPromptFeatureBindingRequest;
import com.lexiflow.ai.prompt.dto.AiPromptFeatureGroupResponse;
import com.lexiflow.ai.prompt.dto.AiPromptTemplateRequest;
import com.lexiflow.ai.prompt.mapper.AiPromptFeatureBindingMapper;
import com.lexiflow.ai.prompt.mapper.AiPromptTemplateMapper;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AiPromptTemplateServiceTest {

    @Mock
    private AiPromptTemplateMapper templateMapper;
    @Mock
    private AiPromptFeatureBindingMapper bindingMapper;
    @Mock
    private WordbookMapper wordbookMapper;

    private AiPromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new AiPromptTemplateService(templateMapper, bindingMapper, new DefaultAiPromptRegistry(), wordbookMapper);
    }

    @Test
    void resolveShouldUseBuiltinWhenNoBindingExists() {
        when(bindingMapper.selectOne(any())).thenReturn(null);

        ResolvedAiPromptTemplate resolved = service.resolve(AiPromptFeatureType.WORD_QA);

        assertThat(resolved.builtIn()).isTrue();
        assertThat(resolved.templateId()).isNull();
        assertThat(resolved.systemPrompt()).contains("LexiFlow");
        assertThat(resolved.cacheFingerprint()).startsWith("builtin:WORD_QA:");
    }

    @Test
    void resolveShouldUseEnabledActiveCustomTemplate() {
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setFeatureType(AiPromptFeatureType.CLOZE_QUIZ);
        binding.setWordbookId(0L);
        binding.setTemplateId(12L);
        AiPromptTemplate template = customTemplate(12L, AiPromptFeatureType.CLOZE_QUIZ, "完形模板");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(templateMapper.selectById(12L)).thenReturn(template);

        ResolvedAiPromptTemplate resolved = service.resolve(AiPromptFeatureType.CLOZE_QUIZ);

        assertThat(resolved.builtIn()).isFalse();
        assertThat(resolved.templateId()).isEqualTo(12L);
        assertThat(resolved.templateName()).isEqualTo("完形模板");
        assertThat(resolved.cacheFingerprint()).startsWith("template:12:");
    }

    @Test
    void resolveShouldPreferWordbookScopedTemplate() {
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setFeatureType(AiPromptFeatureType.WORD_QA);
        binding.setWordbookId(2L);
        binding.setTemplateId(22L);
        AiPromptTemplate template = customTemplate(22L, AiPromptFeatureType.WORD_QA, "六级专用模板");
        template.setWordbookId(2L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(templateMapper.selectById(22L)).thenReturn(template);

        ResolvedAiPromptTemplate resolved = service.resolve(AiPromptFeatureType.WORD_QA, 2L);

        assertThat(resolved.templateId()).isEqualTo(22L);
        assertThat(resolved.wordbookId()).isEqualTo(2L);
        assertThat(resolved.templateName()).isEqualTo("六级专用模板");
        assertThat(resolved.cacheFingerprint()).contains(":wordbook:2:");
    }

    @Test
    void resolveShouldReuseInMemoryCacheForSameFeature() {
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setFeatureType(AiPromptFeatureType.CLOZE_QUIZ);
        binding.setWordbookId(0L);
        binding.setTemplateId(12L);
        AiPromptTemplate template = customTemplate(12L, AiPromptFeatureType.CLOZE_QUIZ, "完形模板");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(templateMapper.selectById(12L)).thenReturn(template);

        service.resolve(AiPromptFeatureType.CLOZE_QUIZ);
        service.resolve(AiPromptFeatureType.CLOZE_QUIZ);

        verify(templateMapper, times(1)).selectById(12L);
    }

    @Test
    void listGroupsShouldMarkBuiltinActiveWhenNoEnabledCustomBindingExists() {
        AiPromptTemplate template = customTemplate(3L, AiPromptFeatureType.CLOZE_REVIEW, "评阅模板");
        template.setEnabled(false);
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setFeatureType(AiPromptFeatureType.CLOZE_REVIEW);
        binding.setWordbookId(0L);
        binding.setTemplateId(3L);
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding));

        List<AiPromptFeatureGroupResponse> groups = service.listGroups();

        AiPromptFeatureGroupResponse reviewGroup = groups.stream()
                .filter(group -> group.featureType() == AiPromptFeatureType.CLOZE_REVIEW)
                .findFirst()
                .orElseThrow();
        assertThat(reviewGroup.usingDefault()).isTrue();
        assertThat(reviewGroup.builtinTemplate().active()).isTrue();
        assertThat(reviewGroup.templates()).hasSize(1);
        assertThat(reviewGroup.templates().get(0).active()).isFalse();
    }

    @Test
    void listGroupsShouldExposeInheritedGlobalTemplateForWordbookScope() {
        AiPromptTemplate template = customTemplate(4L, AiPromptFeatureType.WORD_QA, "全部词书问答模板");
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setFeatureType(AiPromptFeatureType.WORD_QA);
        binding.setWordbookId(0L);
        binding.setTemplateId(4L);
        when(templateMapper.selectList(any())).thenReturn(List.of(template));
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding));

        List<AiPromptFeatureGroupResponse> groups = service.listGroups(2L);

        AiPromptFeatureGroupResponse wordQaGroup = groups.stream()
                .filter(group -> group.featureType() == AiPromptFeatureType.WORD_QA)
                .findFirst()
                .orElseThrow();
        assertThat(wordQaGroup.usingDefault()).isFalse();
        assertThat(wordQaGroup.inheritedTemplate()).isNotNull();
        assertThat(wordQaGroup.inheritedTemplate().id()).isEqualTo("4");
        assertThat(wordQaGroup.templates()).isEmpty();
    }

    @Test
    void copyBuiltinShouldCreateEditableTemplateCopy() {
        ArgumentCaptor<AiPromptTemplate> captor = ArgumentCaptor.forClass(AiPromptTemplate.class);

        service.copyBuiltin(7L, AiPromptFeatureType.WORD_QA);

        verify(templateMapper).insert(captor.capture());
        AiPromptTemplate copied = captor.getValue();
        assertThat(copied.getFeatureType()).isEqualTo(AiPromptFeatureType.WORD_QA);
        assertThat(copied.getName()).contains("副本");
        assertThat(copied.getSourceBuiltinKey()).isEqualTo("builtin:WORD_QA");
        assertThat(copied.getCreatedBy()).isEqualTo(7L);
        assertThat(copied.getEnabled()).isTrue();
    }

    @Test
    void createTemplateShouldRejectMissingWordbookScope() {
        when(wordbookMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.createTemplate(7L, templateRequest(99L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("生效词书不存在或未启用");

        verify(templateMapper, never()).insert(any(AiPromptTemplate.class));
    }

    @Test
    void createTemplateShouldRejectDisabledWordbookScope() {
        Wordbook wordbook = enabledWordbook(2L, false);
        when(wordbookMapper.selectById(2L)).thenReturn(wordbook);

        assertThatThrownBy(() -> service.createTemplate(7L, templateRequest(2L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("生效词书不存在或未启用");

        verify(templateMapper, never()).insert(any(AiPromptTemplate.class));
    }

    @Test
    void createTemplateShouldAllowEnabledWordbookScope() {
        Wordbook wordbook = enabledWordbook(2L, true);
        when(wordbookMapper.selectById(2L)).thenReturn(wordbook);
        ArgumentCaptor<AiPromptTemplate> captor = ArgumentCaptor.forClass(AiPromptTemplate.class);

        service.createTemplate(7L, templateRequest(2L));

        verify(templateMapper).insert(captor.capture());
        assertThat(captor.getValue().getWordbookId()).isEqualTo(2L);
    }

    @Test
    void createTemplateShouldRejectNegativeWordbookScope() {
        assertThatThrownBy(() -> service.createTemplate(7L, templateRequest(-1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("生效词书不存在或未启用");

        verify(wordbookMapper, never()).selectById(any());
        verify(templateMapper, never()).insert(any(AiPromptTemplate.class));
    }

    @Test
    void bindFeatureShouldClearTemplateIdWhenRestoringDefault() {
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setId(9L);
        binding.setFeatureType(AiPromptFeatureType.WORD_QA);
        binding.setWordbookId(0L);
        binding.setTemplateId(12L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);

        service.bindFeature(7L, AiPromptFeatureType.WORD_QA, new AiPromptFeatureBindingRequest(0L, null));

        verify(bindingMapper).update(isNull(), any());
        verify(bindingMapper, never()).updateById(any(AiPromptFeatureBinding.class));
    }

    @Test
    void bindFeatureShouldRejectMissingWordbookScope() {
        when(wordbookMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.bindFeature(7L, AiPromptFeatureType.WORD_QA, new AiPromptFeatureBindingRequest(99L, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("生效词书不存在或未启用");

        verify(bindingMapper, never()).insert(any(AiPromptFeatureBinding.class));
    }

    private AiPromptTemplate customTemplate(Long id, AiPromptFeatureType featureType, String name) {
        AiPromptTemplate template = new AiPromptTemplate();
        template.setId(id);
        template.setFeatureType(featureType);
        template.setWordbookId(0L);
        template.setName(name);
        template.setSystemPrompt("system");
        template.setInstructionPrompt("instruction");
        template.setEnabled(true);
        template.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 10, 0));
        template.setDeleted(0);
        return template;
    }

    private AiPromptTemplateRequest templateRequest(Long wordbookId) {
        return new AiPromptTemplateRequest(
                AiPromptFeatureType.WORD_QA,
                wordbookId,
                "词书专用模板",
                "system",
                "instruction",
                true
        );
    }

    private Wordbook enabledWordbook(Long id, boolean enabled) {
        Wordbook wordbook = new Wordbook();
        wordbook.setId(id);
        wordbook.setEnabled(enabled);
        wordbook.setDeleted(0);
        return wordbook;
    }
}
