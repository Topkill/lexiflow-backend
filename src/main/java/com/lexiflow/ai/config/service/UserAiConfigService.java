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

/**
 * 用户私有AI配置服务类
 * <p>
 * 该服务提供对用户个人AI配置的增删改查操作，包括配置的查询、保存等功能。
 * 每个用户可以拥有自己的AI配置，包括API地址、密钥、模型名称等参数。
 * API密钥会以加密形式存储以确保安全性。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserAiConfigService {

    private final UserAiConfigMapper userAiConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;

    /**
     * 查询指定用户的AI配置信息
     * <p>
     * 该方法根据用户ID查询其个人AI配置信息。如果用户尚未配置，则返回空的配置对象。
     * </p>
     *
     * @param userId 用户ID
     * @return UserAiConfigResponse 用户AI配置响应对象，包含用户的AI配置详情或空配置
     */
    public UserAiConfigResponse getConfig(Long userId) {
        UserAiConfig config = findByUserId(userId);
        return config == null ? UserAiConfigResponse.empty() : UserAiConfigResponse.from(config);
    }

    /**
     * 保存用户的AI配置信息
     * <p>
     * 该方法用于创建或更新用户的个人AI配置。如果用户已有配置则进行更新操作，
     * 否则创建新的配置记录。API密钥会被加密后存储。
     * 配置信息包括API地址、密钥、模型名称、温度参数、流式输出开关等。
     * </p>
     *
     * @param userId 用户ID，用于关联配置到指定用户
     * @param request 保存AI配置请求对象，包含API地址、密钥、模型名称、温度参数等配置信息
     * @return UserAiConfigResponse 保存后的用户AI配置响应对象，包含完整的配置详情
     */
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
        config.setStreamEnabled(Boolean.TRUE.equals(request.streamEnabled()));
        config.setEnabled(request.enabled());
        if (config.getId() == null) {
            userAiConfigMapper.insert(config);
        } else {
            userAiConfigMapper.updateById(config);
        }
        return UserAiConfigResponse.from(config);
    }

    /**
     * 根据用户ID查询用户AI配置实体
     * <p>
     * 该方法从数据库中查询指定用户的AI配置信息，最多返回一条记录。
     * </p>
     *
     * @param userId 用户ID
     * @return UserAiConfig 查询到的用户AI配置实体对象，如果不存在则返回null
     */
    private UserAiConfig findByUserId(Long userId) {
        return userAiConfigMapper.selectOne(new LambdaQueryWrapper<UserAiConfig>()
                .eq(UserAiConfig::getUserId, userId)
                .last("LIMIT 1"));
    }
}
