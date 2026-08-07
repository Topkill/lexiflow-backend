package com.lexiflow.ai.config.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 公共配置实体，对应数据库 {@code ai_public_config} 表。
 *
 * <p>存储系统级别的 AI 服务配置，包括 API 地址、加密后的 API Key、模型名称、温度参数等。
 * 支持乐观锁版本控制，同一时间只能有一个配置处于激活状态。</p>
 */
@Getter
@Setter
@TableName("ai_public_config")
public class AiPublicConfig {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置名称 */
    private String name;
    /** AI 服务 API 基础地址 */
    private String apiBaseUrl;
    /** AES/GCM 加密后的 API Key */
    private String encryptedApiKey;
    /** 模型名称，如 gpt-4o */
    private String modelName;
    /** 温度参数，控制生成随机性 */
    private BigDecimal temperature;
    /** 是否启用流式输出 */
    private Boolean streamEnabled;
    /** 每用户每日调用配额 */
    private Integer dailyQuotaPerUser;
    /** 是否为当前激活的配置 */
    private Boolean active;
    /** 是否启用 */
    private Boolean enabled;
    /** 备注信息 */
    private String remark;
    /** 创建人 ID */
    private Long createdBy;
    /** 更新人 ID */
    private Long updatedBy;

    /** 创建时间，由 MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间，由 MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
