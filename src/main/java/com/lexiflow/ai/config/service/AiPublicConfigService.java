package com.lexiflow.ai.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lexiflow.ai.config.domain.AiPublicConfig;
import com.lexiflow.ai.config.dto.AiPublicConfigRequest;
import com.lexiflow.ai.config.dto.AiPublicConfigResponse;
import com.lexiflow.ai.config.mapper.AiPublicConfigMapper;
import com.lexiflow.ai.core.service.AiConfigResolver;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.crypto.ApiKeyCryptoService;
import com.lexiflow.infra.redis.RedisCacheInvalidationPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiPublicConfigService {

    private final AiPublicConfigMapper aiPublicConfigMapper;
    private final ApiKeyCryptoService apiKeyCryptoService;
    private final AiConfigResolver aiConfigResolver;
    private final RedisCacheInvalidationPublisher cacheInvalidationPublisher;

    /**
     * 查询所有公共 AI 配置列表
     * <p>
     * 该方法从数据库中查询所有公共AI配置记录，按照创建时间和ID升序排列，
     * 并将实体对象转换为响应DTO对象返回。
     * </p>
     *
     * @return List&lt;AiPublicConfigResponse&gt; 公共AI配置响应列表，包含所有配置的详细信息
     */
    public List<AiPublicConfigResponse> listConfigs() {
        return aiPublicConfigMapper.selectList(new LambdaQueryWrapper<AiPublicConfig>()
                        .orderByAsc(AiPublicConfig::getCreatedAt)
                        .orderByAsc(AiPublicConfig::getId))
                .stream()
                .map(AiPublicConfigResponse::from)
                .toList();
    }


        /**
         * 创建新的公共 AI 配置
         * <p>
         * 该方法创建一个新的公共AI配置，新配置默认处于非激活状态。
         * API密钥会被加密存储，配置信息包括API地址、模型名称、温度参数等。
         * 创建成功后会清除运行时缓存以确保数据一致性。
         * </p>
         *
         * @param adminUserId 管理员用户ID，用于记录创建人和更新人
         * @param request AI配置请求对象，包含配置名称、API地址、API密钥、模型参数等信息
         * @return AiPublicConfigResponse 创建成功后的AI配置响应对象，包含完整的配置详情
         */
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
            evictRuntimeConfig();
            return AiPublicConfigResponse.from(config);
        }

        /**
         * 更新指定的公共 AI 配置
         * <p>
         * 该方法根据配置ID查找现有配置并更新其信息。
         * 更新内容包括API地址、模型名称、温度参数等，API密钥如果提供则会被加密更新。
         * 更新成功后会清除运行时缓存以确保数据一致性。
         * </p>
         *
         * @param adminUserId 管理员用户ID，用于记录更新人
         * @param configId 要更新的AI配置ID
         * @param request AI配置请求对象，包含需要更新的配置信息
         * @return AiPublicConfigResponse 更新后的AI配置响应对象，包含最新的配置详情
         */
        @Transactional
        public AiPublicConfigResponse updateConfig(Long adminUserId, Long configId, AiPublicConfigRequest request) {
            AiPublicConfig config = getConfig(configId);
            applyRequest(config, request, false);
            config.setUpdatedBy(adminUserId);
            aiPublicConfigMapper.updateById(config);
            evictRuntimeConfig();
            return AiPublicConfigResponse.from(config);
        }

        /**
         * 激活指定的公共 AI 配置
         * <p>
         * 该方法将指定的配置设置为激活状态，同时会将之前激活的配置设为非激活状态，
         * 确保系统中只有一个公共AI配置处于激活状态。
         * 只有已启用的配置才能被激活，否则会抛出业务异常。
         * 操作完成后会清除运行时缓存以确保数据一致性。
         * </p>
         *
         * @param adminUserId 管理员用户ID，用于记录更新人
         * @param configId 要激活的AI配置ID
         * @throws BizException 当配置未启用时抛出异常，提示"公共 AI 配置未启用，不能激活"
         */
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
            evictRuntimeConfig();
        }

        /**
         * 启用指定的公共 AI 配置
         * <p>
         * 该方法将指定的配置设置为启用状态，使其可以被使用。
         * 启用操作不会自动激活该配置，需要单独调用激活方法。
         * 操作完成后会清除运行时缓存以确保数据一致性。
         * </p>
         *
         * @param adminUserId 管理员用户ID，用于记录更新人
         * @param configId 要启用的AI配置ID
         */
        @Transactional
        public void enableConfig(Long adminUserId, Long configId) {
            AiPublicConfig config = getConfig(configId);
            config.setEnabled(true);
            config.setUpdatedBy(adminUserId);
            aiPublicConfigMapper.updateById(config);
            evictRuntimeConfig();
        }

        /**
         * 禁用指定的公共 AI 配置
         * <p>
         * 该方法将指定的配置设置为禁用状态，并同时取消其激活状态。
         * 禁用后的配置不能被激活，需要先启用才能再次使用。
         * 操作完成后会清除运行时缓存以确保数据一致性。
         * </p>
         *
         * @param adminUserId 管理员用户ID，用于记录更新人
         * @param configId 要禁用的AI配置ID
         */
        @Transactional
        public void disableConfig(Long adminUserId, Long configId) {
            AiPublicConfig config = getConfig(configId);
            config.setEnabled(false);
            config.setActive(false);
            config.setUpdatedBy(adminUserId);
            aiPublicConfigMapper.updateById(config);
            evictRuntimeConfig();
        }

        /**
         * 获取指定的公共 AI 配置对象
         * <p>
         * 该方法根据配置ID从数据库中查询对应的AI配置信息。
         * 如果配置不存在，则抛出业务异常。
         * </p>
         *
         * @param configId AI配置ID
         * @return AiPublicConfig 查询到的AI配置实体对象
         * @throws BizException 当配置不存在时抛出异常，提示"公共 AI 配置不存在"
         */
        private AiPublicConfig getConfig(Long configId) {
            AiPublicConfig config = aiPublicConfigMapper.selectById(configId);
            if (config == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "公共 AI 配置不存在");
            }
            return config;
        }

        /**
         * 清除运行时AI配置缓存
         * <p>
         * 该方法在事务提交后执行缓存清除操作，确保数据一致性。
         * 会清除AI配置解析器中的公共配置缓存，并发布缓存失效事件到Redis，
         * 以便在分布式环境中同步清除其他节点的缓存。
         * </p>
         */
        private void evictRuntimeConfig() {
            runAfterCommitOrNow(() -> {
                aiConfigResolver.evictPublicConfigCache();
                cacheInvalidationPublisher.publishPublicAiConfigEvict();
            });
        }

        /**
         * 在事务提交后或立即执行指定任务
         * <p>
         * 该方法判断当前是否存在活跃的事务同步管理器。
         * 如果存在，则注册事务同步回调，在事务提交后执行任务；
         * 如果不存在，则立即执行任务。
         * 这种设计确保了在有事务上下文时等待事务提交，避免缓存不一致问题。
         * </p>
         *
         * @param task 要执行的任务Runnable对象
         */
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

        /**
         * 将请求对象的配置信息应用到AI配置实体上
         * <p>
         * 该方法负责将DTO中的配置信息复制到实体对象中，并进行必要的数据处理。
         * 包括字符串修剪、API密钥加密、状态同步等操作。
         * 对于新建配置，可以要求API密钥必须提供；对于更新配置，API密钥为可选。
         * 如果配置被禁用，会自动将其设置为非激活状态。
         * </p>
         *
         * @param config AI配置实体对象，用于接收请求中的配置信息
         * @param request AI配置请求对象，包含待应用的配置信息
         * @param apiKeyRequired 是否要求API密钥必须提供，新建配置时为true，更新时为false
         * @throws BizException 当apiKeyRequired为true但API密钥为空时抛出异常，提示"API Key 不能为空"
         */
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
