package com.lexiflow.ai.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.ai.config.domain.AiPublicConfig;
import com.lexiflow.ai.config.dto.AiPublicConfigRequest;
import com.lexiflow.ai.config.dto.AiPublicConfigResponse;
import com.lexiflow.ai.config.mapper.AiPublicConfigMapper;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.crypto.ApiKeyCryptoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiPublicConfigService {

    private final AiPublicConfigMapper aiPublicConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;

    public List<AiPublicConfigResponse> listConfigs() {
        return aiPublicConfigMapper.selectList(new LambdaQueryWrapper<AiPublicConfig>()
                        .orderByAsc(AiPublicConfig::getCreatedAt)
                        .orderByAsc(AiPublicConfig::getId))
                .stream()
                .map(AiPublicConfigResponse::from)
                .toList();
    }

    @Transactional
    public AiPublicConfigResponse createConfig(Long adminUserId, AiPublicConfigRequest request) {
        AiPublicConfig config = new AiPublicConfig();
        applyRequest(config, request, true);
        config.setActive(false);
        config.setCreatedBy(adminUserId);
        config.setUpdatedBy(adminUserId);
        config.setDeleted(0);
        config.setVersion(0);
        aiPublicConfigMapper.insert(config);
        return AiPublicConfigResponse.from(config);
    }

    @Transactional
    public AiPublicConfigResponse updateConfig(Long adminUserId, Long configId, AiPublicConfigRequest request) {
        AiPublicConfig config = getConfig(configId);
        applyRequest(config, request, false);
        config.setUpdatedBy(adminUserId);
        aiPublicConfigMapper.updateById(config);
        return AiPublicConfigResponse.from(config);
    }

    @Transactional
    public void activateConfig(Long adminUserId, Long configId) {
        AiPublicConfig config = getConfig(configId);
        if (!config.getEnabled()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "公共 AI 配置未启用，不能激活");
        }
        aiPublicConfigMapper.update(null, new LambdaUpdateWrapper<AiPublicConfig>()
                .set(AiPublicConfig::getActive, false)
                .eq(AiPublicConfig::getActive, true));
        config.setActive(true);
        config.setUpdatedBy(adminUserId);
        aiPublicConfigMapper.updateById(config);
    }

    @Transactional
    public void enableConfig(Long adminUserId, Long configId) {
        AiPublicConfig config = getConfig(configId);
        config.setEnabled(true);
        config.setUpdatedBy(adminUserId);
        aiPublicConfigMapper.updateById(config);
    }

    @Transactional
    public void disableConfig(Long adminUserId, Long configId) {
        AiPublicConfig config = getConfig(configId);
        config.setEnabled(false);
        config.setActive(false);
        config.setUpdatedBy(adminUserId);
        aiPublicConfigMapper.updateById(config);
    }

    private AiPublicConfig getConfig(Long configId) {
        AiPublicConfig config = aiPublicConfigMapper.selectById(configId);
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "公共 AI 配置不存在");
        }
        return config;
    }

    private void applyRequest(AiPublicConfig config, AiPublicConfigRequest request, boolean apiKeyRequired) {
        if (apiKeyRequired && !StringUtils.hasText(request.apiKey())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "API Key 不能为空");
        }
        config.setName(request.name().trim());
        config.setApiBaseUrl(request.apiBaseUrl().trim());
        if (StringUtils.hasText(request.apiKey())) {
            config.setEncryptedApiKey(apiKeyCryptoService.encrypt(request.apiKey().trim()));
        }
        config.setModelName(request.modelName().trim());
        config.setTemperature(request.temperature());
        config.setStreamEnabled(Boolean.TRUE.equals(request.streamEnabled()));
        config.setDailyQuotaPerUser(request.dailyQuotaPerUser());
        config.setEnabled(request.enabled());
        config.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        if (Boolean.FALSE.equals(request.enabled())) {
            config.setActive(false);
        }
    }
}
