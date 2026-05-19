package com.lexiflow.ai.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.ai.config.domain.UserAiConfig;
import com.lexiflow.ai.config.dto.SaveUserAiConfigRequest;
import com.lexiflow.ai.config.dto.UserAiConfigResponse;
import com.lexiflow.ai.config.mapper.UserAiConfigMapper;
import com.lexiflow.infra.crypto.ApiKeyCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAiConfigService {

    private final UserAiConfigMapper userAiConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;

    public UserAiConfigResponse getConfig(Long userId) {
        UserAiConfig config = findByUserId(userId);
        return config == null ? UserAiConfigResponse.empty() : UserAiConfigResponse.from(config);
    }

    @Transactional
    public UserAiConfigResponse saveConfig(Long userId, SaveUserAiConfigRequest request) {
        UserAiConfig config = findByUserId(userId);
        if (config == null) {
            config = new UserAiConfig();
            config.setUserId(userId);
            config.setDeleted(0);
        }
        config.setApiBaseUrl(request.apiBaseUrl().trim());
        config.setEncryptedApiKey(apiKeyCryptoService.encrypt(request.apiKey().trim()));
        config.setModelName(request.modelName().trim());
        config.setTemperature(request.temperature());
        config.setEnabled(request.enabled());
        if (config.getId() == null) {
            userAiConfigMapper.insert(config);
        } else {
            userAiConfigMapper.updateById(config);
        }
        return UserAiConfigResponse.from(config);
    }

    private UserAiConfig findByUserId(Long userId) {
        return userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .last("LIMIT 1"));
    }
}
