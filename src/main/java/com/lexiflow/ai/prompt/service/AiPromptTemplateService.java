package com.lexiflow.ai.prompt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureBinding;
import com.lexiflow.ai.prompt.domain.AiPromptFeatureType;
import com.lexiflow.ai.prompt.domain.AiPromptTemplate;
import com.lexiflow.ai.prompt.dto.AiPromptFeatureBindingRequest;
import com.lexiflow.ai.prompt.dto.AiPromptFeatureGroupResponse;
import com.lexiflow.ai.prompt.dto.AiPromptTemplateRequest;
import com.lexiflow.ai.prompt.dto.AiPromptTemplateResponse;
import com.lexiflow.ai.prompt.mapper.AiPromptFeatureBindingMapper;
import com.lexiflow.ai.prompt.mapper.AiPromptTemplateMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiPromptTemplateService {

    private final AiPromptTemplateMapper templateMapper;
    private final AiPromptFeatureBindingMapper bindingMapper;
    private final DefaultAiPromptRegistry defaultRegistry;

    public List<AiPromptFeatureGroupResponse> listGroups() {
        List<AiPromptTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<AiPromptTemplate>()
                .orderByAsc(AiPromptTemplate::getFeatureType)
                .orderByAsc(AiPromptTemplate::getCreatedAt)
                .orderByAsc(AiPromptTemplate::getId));
        List<AiPromptFeatureBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<AiPromptFeatureBinding>());
        return Arrays.stream(AiPromptFeatureType.values())
                .map(featureType -> buildGroup(featureType, templates, bindings))
                .toList();
    }

    public ResolvedAiPromptTemplate resolve(AiPromptFeatureType featureType) {
        AiPromptFeatureBinding binding = getBinding(featureType);
        if (binding != null && binding.getTemplateId() != null) {
            AiPromptTemplate template = templateMapper.selectById(binding.getTemplateId());
            if (template != null
                    && template.getFeatureType() == featureType
                    && Boolean.TRUE.equals(template.getEnabled())) {
                return new ResolvedAiPromptTemplate(
                        featureType,
                        template.getId(),
                        template.getName(),
                        template.getSystemPrompt(),
                        template.getInstructionPrompt(),
                        false,
                        customFingerprint(template)
                );
            }
        }
        return resolveBuiltin(featureType);
    }

    @Transactional
    public AiPromptTemplateResponse createTemplate(Long adminUserId, AiPromptTemplateRequest request) {
        AiPromptTemplate template = new AiPromptTemplate();
        applyRequest(template, request);
        template.setCreatedBy(adminUserId);
        template.setUpdatedBy(adminUserId);
        template.setDeleted(0);
        template.setVersion(0);
        templateMapper.insert(template);
        return AiPromptTemplateResponse.from(template, isActive(template));
    }

    @Transactional
    public AiPromptTemplateResponse updateTemplate(Long adminUserId, Long templateId, AiPromptTemplateRequest request) {
        AiPromptTemplate template = getTemplate(templateId);
        if (template.getFeatureType() != request.featureType()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "模板功能类型不可修改");
        }
        applyRequest(template, request);
        template.setUpdatedBy(adminUserId);
        templateMapper.updateById(template);
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            clearBindingIfActive(adminUserId, template.getId());
        }
        return AiPromptTemplateResponse.from(template, isActive(template));
    }

    @Transactional
    public AiPromptTemplateResponse copyTemplate(Long adminUserId, Long templateId) {
        AiPromptTemplate source = getTemplate(templateId);
        AiPromptTemplate copied = new AiPromptTemplate();
        copied.setFeatureType(source.getFeatureType());
        copied.setName(copyName(source.getName()));
        copied.setSystemPrompt(source.getSystemPrompt());
        copied.setInstructionPrompt(source.getInstructionPrompt());
        copied.setEnabled(true);
        copied.setSourceTemplateId(source.getId());
        copied.setCreatedBy(adminUserId);
        copied.setUpdatedBy(adminUserId);
        copied.setDeleted(0);
        copied.setVersion(0);
        templateMapper.insert(copied);
        return AiPromptTemplateResponse.from(copied, false);
    }

    @Transactional
    public AiPromptTemplateResponse copyBuiltin(Long adminUserId, AiPromptFeatureType featureType) {
        DefaultAiPromptDefinition builtin = defaultRegistry.get(featureType);
        if (builtin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "内置提示词不存在");
        }
        AiPromptTemplate copied = new AiPromptTemplate();
        copied.setFeatureType(featureType);
        copied.setName(copyName(builtin.name()));
        copied.setSystemPrompt(builtin.systemPrompt());
        copied.setInstructionPrompt(builtin.instructionPrompt());
        copied.setEnabled(true);
        copied.setSourceBuiltinKey(builtin.templateKey());
        copied.setCreatedBy(adminUserId);
        copied.setUpdatedBy(adminUserId);
        copied.setDeleted(0);
        copied.setVersion(0);
        templateMapper.insert(copied);
        return AiPromptTemplateResponse.from(copied, false);
    }

    @Transactional
    public void bindFeature(Long adminUserId, AiPromptFeatureType featureType, AiPromptFeatureBindingRequest request) {
        Long templateId = request == null ? null : request.templateId();
        if (templateId != null) {
            AiPromptTemplate template = getTemplate(templateId);
            if (template.getFeatureType() != featureType) {
                throw new BizException(ErrorCode.BAD_REQUEST, "提示词模板不属于当前 AI 功能");
            }
            if (!Boolean.TRUE.equals(template.getEnabled())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "停用的提示词模板不能设为当前使用");
            }
        }

        AiPromptFeatureBinding binding = getBinding(featureType);
        if (binding == null) {
            binding = new AiPromptFeatureBinding();
            binding.setFeatureType(featureType);
            binding.setDeleted(0);
        }
        binding.setTemplateId(templateId);
        binding.setUpdatedBy(adminUserId);
        if (binding.getId() == null) {
            bindingMapper.insert(binding);
        } else if (templateId == null) {
            bindingMapper.update(null, new UpdateWrapper<AiPromptFeatureBinding>()
                    .eq("id", binding.getId())
                    .set("template_id", null)
                    .set("updated_by", adminUserId));
        } else {
            bindingMapper.updateById(binding);
        }
    }

    private AiPromptFeatureGroupResponse buildGroup(
            AiPromptFeatureType featureType,
            List<AiPromptTemplate> allTemplates,
            List<AiPromptFeatureBinding> allBindings
    ) {
        AiPromptFeatureBinding binding = allBindings.stream()
                .filter(item -> item.getFeatureType() == featureType)
                .findFirst()
                .orElse(null);
        Long activeTemplateId = activeTemplateId(featureType, binding, allTemplates);
        boolean usingDefault = activeTemplateId == null;
        DefaultAiPromptDefinition builtin = defaultRegistry.get(featureType);
        AiPromptTemplateResponse builtinResponse = AiPromptTemplateResponse.builtin(
                featureType,
                builtin.name(),
                builtin.systemPrompt(),
                builtin.instructionPrompt(),
                usingDefault,
                builtin.templateKey()
        );
        List<AiPromptTemplateResponse> templates = allTemplates.stream()
                .filter(template -> template.getFeatureType() == featureType)
                .map(template -> AiPromptTemplateResponse.from(template, activeTemplateId != null && activeTemplateId.equals(template.getId())))
                .toList();
        return new AiPromptFeatureGroupResponse(
                featureType,
                featureType.label(),
                usingDefault,
                activeTemplateId == null ? null : String.valueOf(activeTemplateId),
                builtinResponse,
                templates
        );
    }

    private Long activeTemplateId(AiPromptFeatureType featureType, AiPromptFeatureBinding binding, List<AiPromptTemplate> templates) {
        if (binding == null || binding.getTemplateId() == null) {
            return null;
        }
        return templates.stream()
                .filter(template -> binding.getTemplateId().equals(template.getId()))
                .filter(template -> template.getFeatureType() == featureType)
                .filter(template -> Boolean.TRUE.equals(template.getEnabled()))
                .map(AiPromptTemplate::getId)
                .findFirst()
                .orElse(null);
    }

    private AiPromptFeatureBinding getBinding(AiPromptFeatureType featureType) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<AiPromptFeatureBinding>()
                .eq(AiPromptFeatureBinding::getFeatureType, featureType)
                .last("LIMIT 1"));
    }

    private AiPromptTemplate getTemplate(Long templateId) {
        AiPromptTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提示词模板不存在");
        }
        return template;
    }

    private void applyRequest(AiPromptTemplate template, AiPromptTemplateRequest request) {
        template.setFeatureType(request.featureType());
        template.setName(request.name().trim());
        template.setSystemPrompt(request.systemPrompt().trim());
        template.setInstructionPrompt(request.instructionPrompt().trim());
        template.setEnabled(Boolean.TRUE.equals(request.enabled()));
    }

    private boolean isActive(AiPromptTemplate template) {
        AiPromptFeatureBinding binding = getBinding(template.getFeatureType());
        return binding != null
                && binding.getTemplateId() != null
                && binding.getTemplateId().equals(template.getId())
                && Boolean.TRUE.equals(template.getEnabled());
    }

    private void clearBindingIfActive(Long adminUserId, Long templateId) {
        AiPromptFeatureBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<AiPromptFeatureBinding>()
                .eq(AiPromptFeatureBinding::getTemplateId, templateId)
                .last("LIMIT 1"));
        if (binding == null) {
            return;
        }
        bindingMapper.update(null, new UpdateWrapper<AiPromptFeatureBinding>()
                .eq("id", binding.getId())
                .set("template_id", null)
                .set("updated_by", adminUserId));
    }

    private ResolvedAiPromptTemplate resolveBuiltin(AiPromptFeatureType featureType) {
        DefaultAiPromptDefinition builtin = defaultRegistry.get(featureType);
        if (builtin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "内置提示词不存在");
        }
        return new ResolvedAiPromptTemplate(
                featureType,
                null,
                builtin.name(),
                builtin.systemPrompt(),
                builtin.instructionPrompt(),
                true,
                "builtin:" + featureType.name() + ":" + sha256(builtin.systemPrompt() + "\n" + builtin.instructionPrompt()).substring(0, 16)
        );
    }

    private String customFingerprint(AiPromptTemplate template) {
        String updatedAt = template.getUpdatedAt() == null ? "" : template.getUpdatedAt().toString();
        return "template:" + template.getId() + ":" + updatedAt + ":" + sha256(template.getSystemPrompt() + "\n" + template.getInstructionPrompt()).substring(0, 16);
    }

    private String copyName(String sourceName) {
        String base = StringUtils.hasText(sourceName) ? sourceName.trim() : "提示词模板";
        String value = base + " 副本";
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
