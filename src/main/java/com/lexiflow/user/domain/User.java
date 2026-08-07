package com.lexiflow.user.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体，对应数据库 {@code users} 表。
 *
 * <p>存储用户的基本信息、认证凭据、角色状态及登录记录。
 * 支持逻辑删除，并通过 MyBatis-Plus 自动填充创建/更新时间。</p>
 */
@Getter
@Setter
@TableName("users")
public class User {

    /** 用户主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户邮箱，唯一且已归一化（小写） */
    private String email;

    /** BCrypt 加密后的密码哈希 */
    private String passwordHash;

    /** 用户昵称 */
    private String nickname;

    /** 头像 URL 地址 */
    private String avatarUrl;

    /** 用户角色（USER / ADMIN） */
    private UserRole role;

    /** 用户状态（ACTIVE / DISABLED / LOCKED） */
    private UserStatus status;

    /**
     * Token 版本号，用于 JWT 失效机制。
     * 修改密码等安全操作时会递增此值，使旧 Token 失效。
     * 设置为不可通过普通 update 修改，仅通过专用方法递增。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long tokenVersion;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最后登录 IP */
    private String lastLoginIp;

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
