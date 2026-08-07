package com.lexiflow.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.system.config.domain.SystemConfig;
import com.lexiflow.system.config.domain.SystemConfigValueType;
import com.lexiflow.system.config.dto.SystemConfigQueryRequest;
import com.lexiflow.system.config.dto.SystemConfigRequest;
import com.lexiflow.system.config.dto.SystemConfigResponse;
import com.lexiflow.system.config.mapper.SystemConfigMapper;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统配置服务。
 * <p>提供系统配置的增删改查功能，包含配置键格式校验、值类型规范化、
 * 可编辑权限控制和配置键唯一性检查等业务逻辑。</p>
 */
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    /** 配置键格式正则：小写字母开头的点分隔标识符 */
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$");

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    /** 分页查询系统配置列表，支持按值类型、可编辑状态和关键词过滤。 */
    public PageResponse<SystemConfigResponse> pageConfigs(SystemConfigQueryRequest request) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<SystemConfig>()
                .orderByAsc(SystemConfig::getConfigKey)
                .orderByDesc(SystemConfig::getCreatedAt);
        if (request.valueType() != null) {
            wrapper.eq(SystemConfig::getValueType, request.valueType());
        }
        if (request.editable() != null) {
            wrapper.eq(SystemConfig::getEditable, request.editable());
        }
        if (StringUtils.hasText(request.keyword())) {
            String keyword = request.keyword().trim();
            wrapper.and(query -> query.like(SystemConfig::getConfigKey, keyword)
                    .or()
                    .like(SystemConfig::getDescription, keyword));
        }
        Page<SystemConfig> page = systemConfigMapper.selectPage(Page.of(request.page(), request.size()), wrapper);
        return PageResponse.of(
                page.getRecords().stream().map(SystemConfigResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    /** 根据 ID 查询系统配置详情。 */
    public SystemConfigResponse getConfig(Long configId) {
        return SystemConfigResponse.from(requireConfig(configId));
    }

    /** 新增系统配置，校验配置键唯一性并规范化值。 */
    @Transactional
    public SystemConfigResponse createConfig(Long adminUserId, SystemConfigRequest request) {
        String configKey = normalizeConfigKey(request.configKey());
        ensureConfigKeyAvailable(configKey, null);
        SystemConfig config = new SystemConfig();
        applyRequest(config, request, configKey);
        config.setCreatedBy(adminUserId);
        config.setUpdatedBy(adminUserId);
        config.setDeleted(0);
        config.setVersion(0);
        systemConfigMapper.insert(config);
        return SystemConfigResponse.from(config);
    }

    /** 编辑系统配置，校验可编辑权限和配置键唯一性。 */
    @Transactional
    public SystemConfigResponse updateConfig(Long adminUserId, Long configId, SystemConfigRequest request) {
        SystemConfig config = requireConfig(configId);
        ensureEditable(config);
        String configKey = normalizeConfigKey(request.configKey());
        ensureConfigKeyAvailable(configKey, configId);
        applyRequest(config, request, configKey);
        config.setUpdatedBy(adminUserId);
        systemConfigMapper.updateById(config);
        return SystemConfigResponse.from(config);
    }

    /** 删除系统配置，仅可删除标记为可编辑的配置。 */
    @Transactional
    public void deleteConfig(Long configId) {
        SystemConfig config = requireConfig(configId);
        ensureEditable(config);
        systemConfigMapper.deleteById(configId);
    }

    /** 将请求参数应用到配置实体，包含值规范化处理。 */
    private void applyRequest(SystemConfig config, SystemConfigRequest request, String configKey) {
        String normalizedValue = normalizeValue(request.valueType(), request.configValue());
        config.setConfigKey(configKey);
        config.setConfigValue(normalizedValue);
        config.setValueType(request.valueType());
        config.setDescription(trimToNull(request.description()));
        config.setEditable(request.editable());
    }

    /** 规范化配置键：转小写并校验格式。 */
    private String normalizeConfigKey(String configKey) {
        String normalized = configKey.trim().toLowerCase(Locale.ROOT);
        if (!CONFIG_KEY_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "配置键只能使用小写字母、数字、下划线和点号，并且每段必须以字母开头");
        }
        return normalized;
    }

    /** 根据值类型对配置值进行规范化处理。 */
    private String normalizeValue(SystemConfigValueType valueType, String value) {
        String trimmedValue = value == null ? null : value.trim();
        return switch (valueType) {
            case STRING -> trimmedValue;
            case NUMBER -> normalizeNumber(trimmedValue);
            case BOOLEAN -> normalizeBoolean(trimmedValue);
            case JSON -> normalizeJson(trimmedValue);
        };
    }

    /** 规范化 NUMBER 类型值：校验并去除尾随零。 */
    private String normalizeNumber(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "NUMBER 类型配置值不能为空");
        }
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "NUMBER 类型配置值必须是合法数字");
        }
    }

    /** 规范化 BOOLEAN 类型值：仅接受 true/false。 */
    private String normalizeBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "BOOLEAN 类型配置值不能为空");
        }
        if ("true".equalsIgnoreCase(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "false";
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "BOOLEAN 类型配置值只能是 true 或 false");
    }

    /** 规范化 JSON 类型值：解析并重新序列化为标准格式。 */
    private String normalizeJson(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "JSON 类型配置值不能为空");
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "JSON 类型配置值必须是合法 JSON");
        }
    }

    /** 检查配置键是否可用（未被其他配置占用）。 */
    private void ensureConfigKeyAvailable(String configKey, Long excludedId) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey);
        if (excludedId != null) {
            wrapper.ne(SystemConfig::getId, excludedId);
        }
        if (systemConfigMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "配置键已存在");
        }
    }

    /** 根据 ID 查询配置，不存在则抛出异常。 */
    private SystemConfig requireConfig(Long configId) {
        SystemConfig config = systemConfigMapper.selectById(configId);
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "系统配置不存在");
        }
        return config;
    }

    /** 检查配置是否可编辑，不可编辑则抛出异常。 */
    private void ensureEditable(SystemConfig config) {
        if (!Boolean.TRUE.equals(config.getEditable())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前系统配置不可编辑");
        }
    }

    /** 去除空白，空字符串返回 null。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
