package com.lexiflow.user.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户设置实体，对应数据库 {@code user_settings} 表。
 *
 * <p>存储用户的个性化配置，包括学习目标、每日新词数、AI Key 模式、时区等。
 * 每个用户注册时自动创建默认设置记录。</p>
 */
@Getter
@Setter
@TableName("user_settings")
public class UserSettings {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;

    /** 目标考试类型 */
    private TargetExam targetExam;

    /** 每日新词数量上限 */
    private Integer dailyNewWords;

    /** AI Key 使用模式（PUBLIC / PRIVATE） */
    private AiKeyMode aiKeyMode;

    /** 是否启用日报入口 */
    private Boolean enableDailyReport;

    /** 用户时区，如 Asia/Shanghai */
    private String timezone;

    /** 创建时间，由 MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，由 MyBatis-Plus 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic
    private Integer deleted;
}
