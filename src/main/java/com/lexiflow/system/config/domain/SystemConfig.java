package com.lexiflow.system.config.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统配置实体。
 * <p>存储应用的全局配置项，支持多种值类型（STRING/NUMBER/BOOLEAN/JSON），
 * 通过 editable 字段控制是否允许后台编辑。</p>
 */
@Getter
@Setter
@TableName("system_config")
public class SystemConfig {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，格式为小写字母开头的点分隔标识符 */
    private String configKey;
    /** 配置值（已规范化） */
    private String configValue;
    /** 配置值类型 */
    private SystemConfigValueType valueType;
    /** 配置说明 */
    private String description;
    /** 是否允许后台编辑 */
    private Boolean editable;
    /** 创建人 ID */
    private Long createdBy;
    /** 最后更新人 ID */
    private Long updatedBy;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
