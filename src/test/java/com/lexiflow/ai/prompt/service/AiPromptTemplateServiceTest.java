package com.lexiflow.ai.prompt.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.lexiflow.ai.prompt.mapper.AiPromptFeatureBindingMapper;
import com.lexiflow.ai.prompt.mapper.AiPromptTemplateMapper;
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

    private AiPromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new AiPromptTemplateService(templateMapper, bindingMapper, new DefaultAiPromptRegistry());
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
    void resolveShouldReuseInMemoryCacheForSameFeature() {
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setFeatureType(AiPromptFeatureType.CLOZE_QUIZ);
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
    void bindFeatureShouldClearTemplateIdWhenRestoringDefault() {
        AiPromptFeatureBinding binding = new AiPromptFeatureBinding();
        binding.setId(9L);
        binding.setFeatureType(AiPromptFeatureType.WORD_QA);
        binding.setTemplateId(12L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);

        service.bindFeature(7L, AiPromptFeatureType.WORD_QA, new AiPromptFeatureBindingRequest(null));

        verify(bindingMapper).update(isNull(), any());
        verify(bindingMapper, never()).updateById(any(AiPromptFeatureBinding.class));
    }

    private AiPromptTemplate customTemplate(Long id, AiPromptFeatureType featureType, String name) {
        AiPromptTemplate template = new AiPromptTemplate();
        template.setId(id);
        template.setFeatureType(featureType);
        template.setName(name);
        template.setSystemPrompt("system");
        template.setInstructionPrompt("instruction");
        template.setEnabled(true);
        template.setUpdatedAt(LocalDateTime.of(2026, 5, 24, 10, 0));
        template.setDeleted(0);
        return template;
    }
}
