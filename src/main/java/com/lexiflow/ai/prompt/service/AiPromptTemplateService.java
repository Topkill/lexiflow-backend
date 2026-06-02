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
import com.lexiflow.infra.redis.RedisCacheInvalidationListener;
import com.lexiflow.infra.redis.RedisCacheInvalidationPublisher;
import com.lexiflow.infra.redis.RedisKeys;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiPromptTemplateService implements RedisCacheInvalidationListener {

    public static final long ALL_WORDBOOK_ID = 0L;

    private final AiPromptTemplateMapper templateMapper;
    private final AiPromptFeatureBindingMapper bindingMapper;
    private final DefaultAiPromptRegistry defaultRegistry;
    private final WordbookMapper wordbookMapper;
    private final AiPromptOutputSchemaService outputSchemaService;
    private final RedisCacheInvalidationPublisher cacheInvalidationPublisher;
    private final Map<String, ResolvedAiPromptTemplate> resolvedCache = new ConcurrentHashMap<>();

    public List<AiPromptFeatureGroupResponse> listGroups() {
        return listGroups(ALL_WORDBOOK_ID);
    }

    public List<AiPromptFeatureGroupResponse> listGroups(Long wordbookId) {
        Long scopeWordbookId = normalizeWordbookId(wordbookId);
        List<AiPromptTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<AiPromptTemplate>()
                .orderByAsc(AiPromptTemplate::getFeatureType)
                .orderByAsc(AiPromptTemplate::getWordbookId)
                .orderByAsc(AiPromptTemplate::getCreatedAt)
                .orderByAsc(AiPromptTemplate::getId));
        List<AiPromptFeatureBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<AiPromptFeatureBinding>());
        return Arrays.stream(AiPromptFeatureType.values())
                .map(featureType -> buildGroup(featureType, scopeWordbookId, templates, bindings))
                .toList();
    }

    public ResolvedAiPromptTemplate resolve(AiPromptFeatureType featureType) {
        return resolve(featureType, ALL_WORDBOOK_ID);
    }

    public ResolvedAiPromptTemplate resolve(AiPromptFeatureType featureType, Long wordbookId) {
        Long scopeWordbookId = normalizeWordbookId(wordbookId);
        return resolvedCache.computeIfAbsent(cacheKey(featureType, scopeWordbookId), key -> resolveFresh(featureType, scopeWordbookId));
    }

    private ResolvedAiPromptTemplate resolveFresh(AiPromptFeatureType featureType, Long wordbookId) {
        ResolvedAiPromptTemplate scoped = resolveCustom(featureType, wordbookId);
        if (scoped != null) {
            return scoped;
        }
        if (!isAllWordbook(wordbookId)) {
            ResolvedAiPromptTemplate global = resolveCustom(featureType, ALL_WORDBOOK_ID);
            if (global != null) {
                return global;
            }
        }
        return resolveBuiltin(featureType);
    }

    private ResolvedAiPromptTemplate resolveCustom(AiPromptFeatureType featureType, Long wordbookId) {
        AiPromptFeatureBinding binding = getBinding(featureType, wordbookId);
        if (binding != null && binding.getTemplateId() != null) {
            AiPromptTemplate template = templateMapper.selectById(binding.getTemplateId());
            if (template != null
                    && template.getFeatureType() == featureType
                    && normalizeWordbookId(template.getWordbookId()).equals(wordbookId)
                    && Boolean.TRUE.equals(template.getEnabled())) {
                return new ResolvedAiPromptTemplate(
                        featureType,
                        wordbookId,
                        template.getId(),
                        template.getName(),
                        template.getSystemPrompt(),
                        template.getInstructionPrompt(),
                        outputSchemaService.resolveSchemaJson(featureType, template.getOutputSchemaJson()),
                        false,
                        customFingerprint(template)
                );
            }
        }
        return null;
    }

    @Transactional
    public AiPromptTemplateResponse createTemplate(Long adminUserId, AiPromptTemplateRequest request) {
        Long wordbookId = validateWritableWordbookId(request.wordbookId());
        AiPromptTemplate template = new AiPromptTemplate();
        applyRequest(template, request, wordbookId);
        template.setCreatedBy(adminUserId);
        template.setUpdatedBy(adminUserId);
        template.setDeleted(0);
        template.setVersion(0);
        templateMapper.insert(template);
        evict(featureTypeOf(request));
        return responseFrom(template, isActive(template));
    }

    @Transactional
    public AiPromptTemplateResponse updateTemplate(Long adminUserId, Long templateId, AiPromptTemplateRequest request) {
        AiPromptTemplate template = getTemplate(templateId);
        Long wordbookId = validateWritableWordbookId(request.wordbookId());
        if (template.getFeatureType() != request.featureType()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "模板功能类型不可修改");
        }
        if (!normalizeWordbookId(template.getWordbookId()).equals(wordbookId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "模板生效词书不可修改");
        }
        applyRequest(template, request, wordbookId);
        template.setUpdatedBy(adminUserId);
        templateMapper.updateById(template);
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            clearBindingIfActive(adminUserId, template.getId());
        }
        evict(template.getFeatureType());
        return responseFrom(template, isActive(template));
    }

    @Transactional
    public AiPromptTemplateResponse copyTemplate(Long adminUserId, Long templateId) {
        return copyTemplate(adminUserId, templateId, null);
    }

    @Transactional
    public AiPromptTemplateResponse copyTemplate(Long adminUserId, Long templateId, Long targetWordbookId) {
        AiPromptTemplate source = getTemplate(templateId);
        Long wordbookId = validateWritableWordbookId(targetWordbookId == null ? source.getWordbookId() : targetWordbookId);
        AiPromptTemplate copied = new AiPromptTemplate();
        copied.setFeatureType(source.getFeatureType());
        copied.setWordbookId(wordbookId);
        copied.setName(copyName(source.getName()));
        copied.setSystemPrompt(source.getSystemPrompt());
        copied.setInstructionPrompt(source.getInstructionPrompt());
        copied.setOutputSchemaJson(outputSchemaService.resolveSchemaJson(source.getFeatureType(), source.getOutputSchemaJson()));
        copied.setEnabled(true);
        copied.setSourceTemplateId(source.getId());
        copied.setCreatedBy(adminUserId);
        copied.setUpdatedBy(adminUserId);
        copied.setDeleted(0);
        copied.setVersion(0);
        templateMapper.insert(copied);
        evict(copied.getFeatureType());
        return responseFrom(copied, false);
    }

    @Transactional
    public AiPromptTemplateResponse copyBuiltin(Long adminUserId, AiPromptFeatureType featureType) {
        return copyBuiltin(adminUserId, featureType, ALL_WORDBOOK_ID);
    }

    @Transactional
    public AiPromptTemplateResponse copyBuiltin(Long adminUserId, AiPromptFeatureType featureType, Long wordbookId) {
        DefaultAiPromptDefinition builtin = defaultRegistry.get(featureType);
        if (builtin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "内置提示词不存在");
        }
        Long scopeWordbookId = validateWritableWordbookId(wordbookId);
        AiPromptTemplate copied = new AiPromptTemplate();
        copied.setFeatureType(featureType);
        copied.setWordbookId(scopeWordbookId);
        copied.setName(copyName(builtin.name()));
        copied.setSystemPrompt(builtin.systemPrompt());
        copied.setInstructionPrompt(builtin.instructionPrompt());
        copied.setOutputSchemaJson(builtin.outputSchemaJson());
        copied.setEnabled(true);
        copied.setSourceBuiltinKey(builtin.templateKey());
        copied.setCreatedBy(adminUserId);
        copied.setUpdatedBy(adminUserId);
        copied.setDeleted(0);
        copied.setVersion(0);
        templateMapper.insert(copied);
        evict(copied.getFeatureType());
        return responseFrom(copied, false);
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        AiPromptTemplate template = getTemplate(templateId);
        AiPromptFeatureBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<AiPromptFeatureBinding>()
                .eq(AiPromptFeatureBinding::getTemplateId, templateId)
                .last("LIMIT 1"));
        if (binding != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前使用中的提示词模板不能删除，请先恢复默认或切换到其他模板");
        }
        templateMapper.deleteById(templateId);
        evict(template.getFeatureType());
    }

    @Transactional
    public void bindFeature(Long adminUserId, AiPromptFeatureType featureType, AiPromptFeatureBindingRequest request) {
        Long templateId = request == null ? null : request.templateId();
        Long wordbookId = validateWritableWordbookId(request == null ? null : request.wordbookId());
        if (templateId != null) {
            AiPromptTemplate template = getTemplate(templateId);
            if (template.getFeatureType() != featureType) {
                throw new BizException(ErrorCode.BAD_REQUEST, "提示词模板不属于当前 AI 功能");
            }
            if (!normalizeWordbookId(template.getWordbookId()).equals(wordbookId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "提示词模板不属于当前生效词书范围");
            }
            if (!Boolean.TRUE.equals(template.getEnabled())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "停用的提示词模板不能设为当前使用");
            }
        }

        AiPromptFeatureBinding binding = getBinding(featureType, wordbookId);
        if (binding == null) {
            binding = new AiPromptFeatureBinding();
            binding.setFeatureType(featureType);
            binding.setWordbookId(wordbookId);
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
        evict(featureType);
    }

    private AiPromptFeatureGroupResponse buildGroup(
            AiPromptFeatureType featureType,
            Long wordbookId,
            List<AiPromptTemplate> allTemplates,
            List<AiPromptFeatureBinding> allBindings
    ) {
        AiPromptFeatureBinding binding = allBindings.stream()
                .filter(item -> item.getFeatureType() == featureType)
                .filter(item -> normalizeWordbookId(item.getWordbookId()).equals(wordbookId))
                .findFirst()
                .orElse(null);
        Long activeTemplateId = activeTemplateId(featureType, wordbookId, binding, allTemplates);
        AiPromptTemplate inheritedTemplate = activeTemplateId == null
                ? inheritedActiveTemplate(featureType, wordbookId, allBindings, allTemplates)
                : null;
        boolean usingDefault = activeTemplateId == null && inheritedTemplate == null;
        DefaultAiPromptDefinition builtin = defaultRegistry.get(featureType);
        AiPromptTemplateResponse builtinResponse = AiPromptTemplateResponse.builtin(
                featureType,
                builtin.name(),
                builtin.systemPrompt(),
                builtin.instructionPrompt(),
                builtin.outputSchemaJson(),
                usingDefault,
                builtin.templateKey()
        );
        List<AiPromptTemplateResponse> templates = allTemplates.stream()
                .filter(template -> template.getFeatureType() == featureType)
                .filter(template -> normalizeWordbookId(template.getWordbookId()).equals(wordbookId))
                .map(template -> responseFrom(template, activeTemplateId != null && activeTemplateId.equals(template.getId())))
                .toList();
        return new AiPromptFeatureGroupResponse(
                featureType,
                featureType.label(),
                String.valueOf(wordbookId),
                usingDefault,
                activeTemplateId == null ? null : String.valueOf(activeTemplateId),
                builtinResponse,
                inheritedTemplate == null ? null : responseFrom(inheritedTemplate, true),
                templates
        );
    }

    private AiPromptTemplateResponse responseFrom(AiPromptTemplate template, boolean active) {
        return AiPromptTemplateResponse.from(
                template,
                active,
                outputSchemaService.resolveSchemaJson(template.getFeatureType(), template.getOutputSchemaJson())
        );
    }

    private Long activeTemplateId(AiPromptFeatureType featureType, Long wordbookId, AiPromptFeatureBinding binding, List<AiPromptTemplate> templates) {
        if (binding == null || binding.getTemplateId() == null) {
            return null;
        }
        return templates.stream()
                .filter(template -> binding.getTemplateId().equals(template.getId()))
                .filter(template -> template.getFeatureType() == featureType)
                .filter(template -> normalizeWordbookId(template.getWordbookId()).equals(wordbookId))
                .filter(template -> Boolean.TRUE.equals(template.getEnabled()))
                .map(AiPromptTemplate::getId)
                .findFirst()
                .orElse(null);
    }

    private AiPromptTemplate inheritedActiveTemplate(
            AiPromptFeatureType featureType,
            Long wordbookId,
            List<AiPromptFeatureBinding> bindings,
            List<AiPromptTemplate> templates
    ) {
        if (isAllWordbook(wordbookId)) {
            return null;
        }
        AiPromptFeatureBinding globalBinding = bindings.stream()
                .filter(item -> item.getFeatureType() == featureType)
                .filter(item -> isAllWordbook(item.getWordbookId()))
                .findFirst()
                .orElse(null);
        Long globalTemplateId = activeTemplateId(featureType, ALL_WORDBOOK_ID, globalBinding, templates);
        if (globalTemplateId == null) {
            return null;
        }
        return templates.stream()
                .filter(template -> globalTemplateId.equals(template.getId()))
                .findFirst()
                .orElse(null);
    }

    private AiPromptFeatureBinding getBinding(AiPromptFeatureType featureType, Long wordbookId) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<AiPromptFeatureBinding>()
                .eq(AiPromptFeatureBinding::getFeatureType, featureType)
                .eq(AiPromptFeatureBinding::getWordbookId, normalizeWordbookId(wordbookId))
                .last("LIMIT 1"));
    }

    private AiPromptTemplate getTemplate(Long templateId) {
        AiPromptTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提示词模板不存在");
        }
        return template;
    }

    private void applyRequest(AiPromptTemplate template, AiPromptTemplateRequest request, Long wordbookId) {
        template.setFeatureType(request.featureType());
        template.setWordbookId(wordbookId);
        template.setName(request.name().trim());
        template.setSystemPrompt(request.systemPrompt().trim());
        template.setInstructionPrompt(request.instructionPrompt().trim());
        template.setOutputSchemaJson(outputSchemaService.resolveSchemaJson(request.featureType(), request.outputSchemaJson()));
        template.setEnabled(Boolean.TRUE.equals(request.enabled()));
    }

    private boolean isActive(AiPromptTemplate template) {
        AiPromptFeatureBinding binding = getBinding(template.getFeatureType(), template.getWordbookId());
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
        evict(binding.getFeatureType());
    }

    private ResolvedAiPromptTemplate resolveBuiltin(AiPromptFeatureType featureType) {
        DefaultAiPromptDefinition builtin = defaultRegistry.get(featureType);
        if (builtin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "内置提示词不存在");
        }
        return new ResolvedAiPromptTemplate(
                featureType,
                ALL_WORDBOOK_ID,
                null,
                builtin.name(),
                builtin.systemPrompt(),
                builtin.instructionPrompt(),
                builtin.outputSchemaJson(),
                true,
                "builtin:" + featureType.name() + ":" + sha256(builtin.systemPrompt() + "\n" + builtin.instructionPrompt() + "\n" + builtin.outputSchemaJson()).substring(0, 16)
        );
    }

    private String customFingerprint(AiPromptTemplate template) {
        String updatedAt = template.getUpdatedAt() == null ? "" : template.getUpdatedAt().toString();
        return "template:" + template.getId()
                + ":wordbook:" + normalizeWordbookId(template.getWordbookId())
                + ":" + updatedAt
                + ":" + sha256(template.getSystemPrompt() + "\n" + template.getInstructionPrompt() + "\n" + outputSchemaService.resolveSchemaJson(template.getFeatureType(), template.getOutputSchemaJson())).substring(0, 16);
    }

    private String copyName(String sourceName) {
        String base = StringUtils.hasText(sourceName) ? sourceName.trim() : "提示词模板";
        String value = base + " 副本";
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    private void evict(AiPromptFeatureType featureType) {
        runAfterCommitOrNow(() -> {
            evictLocal(featureType);
            cacheInvalidationPublisher.publishPromptEvict(featureType);
        });
    }

    private void evictLocal(AiPromptFeatureType featureType) {
        if (featureType != null) {
            resolvedCache.keySet().removeIf(key -> key.startsWith(featureType.name() + ":"));
        }
    }

    @Override
    public void onCacheInvalidation(String payload) {
        AiPromptFeatureType featureType = RedisKeys.parsePromptEvictPayload(payload);
        if (featureType != null) {
            evictLocal(featureType);
        }
    }

    private AiPromptFeatureType featureTypeOf(AiPromptTemplateRequest request) {
        return request == null ? null : request.featureType();
    }

    private void runAfterCommitOrNow(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private Long validateWritableWordbookId(Long wordbookId) {
        if (wordbookId == null || wordbookId == ALL_WORDBOOK_ID) {
            return ALL_WORDBOOK_ID;
        }
        if (wordbookId < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "生效词书不存在或未启用");
        }
        Wordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null || Integer.valueOf(1).equals(wordbook.getDeleted()) || !Boolean.TRUE.equals(wordbook.getEnabled())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "生效词书不存在或未启用");
        }
        return wordbookId;
    }

    private Long normalizeWordbookId(Long wordbookId) {
        return wordbookId == null || wordbookId <= 0 ? ALL_WORDBOOK_ID : wordbookId;
    }

    private boolean isAllWordbook(Long wordbookId) {
        return normalizeWordbookId(wordbookId) == ALL_WORDBOOK_ID;
    }

    private String cacheKey(AiPromptFeatureType featureType, Long wordbookId) {
        return featureType.name() + ":" + normalizeWordbookId(wordbookId);
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
