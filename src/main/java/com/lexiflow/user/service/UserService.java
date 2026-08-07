package com.lexiflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.auth.service.TokenVersionService;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.dto.ChangePasswordRequest;
import com.lexiflow.user.dto.UpdateProfileRequest;
import com.lexiflow.user.dto.UpdateUserSettingsRequest;
import com.lexiflow.user.mapper.UserMapper;
import com.lexiflow.user.mapper.UserSettingsMapper;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户服务。
 *
 * <p>提供用户注册、查询、资料更新、密码修改、用户设置管理等核心业务逻辑。
 * 密码修改时会同步递增 Token 版本号以使旧 JWT 失效。</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserCacheService authUserCacheService;
    private final TokenVersionService tokenVersionService;
    private final UserSettingsCacheService userSettingsCacheService;

    /**
     * 创建新用户并初始化默认设置。
     *
     * @param email       用户邮箱（会自动归一化为小写）
     * @param rawPassword 明文密码
     * @param nickname    用户昵称
     * @return 创建成功的用户实体
     * @throws BizException 邮箱已注册时抛出
     */
    @Transactional
    public User createUser(String email, String rawPassword, String nickname) {
        String normalizedEmail = normalizeEmail(email);
        if (findByEmail(normalizedEmail) != null) {
            throw new BizException(ErrorCode.EMAIL_REGISTERED);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname.trim());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setDeleted(0);
        userMapper.insert(user);

        createDefaultSettings(user.getId());
        return user;
    }

    /**
     * 根据邮箱查询用户。
     *
     * @param email 邮箱地址
     * @return 用户实体，不存在时返回 null
     */
    public User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, normalizeEmail(email))
                .last("LIMIT 1"));
    }

    /**
     * 根据 ID 查询用户，不存在时抛出异常。
     *
     * @param userId 用户 ID
     * @return 用户实体
     * @throws BizException 用户不存在时抛出 UNAUTHORIZED
     */
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 根据 ID 查询状态为 ACTIVE 的用户。
     *
     * @param userId 用户 ID
     * @return 状态为激活的用户实体
     * @throws BizException 用户不存在或状态非 ACTIVE 时抛出
     */
    public User getActiveUserById(Long userId) {
        User user = getById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return user;
    }

    /** 更新用户最后登录时间和 IP */
    public void updateLoginInfo(Long userId, String clientIp) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
    }

    /**
     * 更新用户个人资料（昵称、头像），并清除认证缓存。
     *
     * @param userId  用户 ID
     * @param request 更新请求
     * @return 更新后的用户实体
     */
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getActiveUserById(userId);
        user.setNickname(request.nickname().trim());
        user.setAvatarUrl(StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null);
        userMapper.updateById(user);
        authUserCacheService.evict(userId);
        return getActiveUserById(userId);
    }

    /**
     * 修改用户密码，成功后递增 Token 版本号使旧 JWT 失效。
     *
     * @param userId  用户 ID
     * @param request 修改密码请求
     * @throws BizException 旧密码不正确时抛出 INVALID_CREDENTIALS
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getActiveUserById(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        tokenVersionService.bumpVersion(userId);
    }

    /**
     * 获取或创建用户设置。
     *
     * <p>优先从 Redis 缓存读取，缓存未命中则查库；
     * 若数据库中也不存在，则创建默认设置记录。</p>
     *
     * @param userId 用户 ID
     * @return 用户设置实体
     */
    public UserSettings getOrCreateSettings(Long userId) {
        UserSettings cached = userSettingsCacheService.get(userId);
        if (cached != null) {
            return cached;
        }
        UserSettings settings = userSettingsMapper.selectOne(new LambdaQueryWrapper<UserSettings>()
                .eq(UserSettings::getUserId, userId)
                .last("LIMIT 1"));
        if (settings != null) {
            userSettingsCacheService.put(settings);
            return settings;
        }
        return createDefaultSettings(userId);
    }

    /**
     * 更新用户设置并同步刷新缓存。
     *
     * @param userId  用户 ID
     * @param request 更新设置请求
     * @return 更新后的设置实体
     */
    public UserSettings updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSettings settings = getOrCreateSettings(userId);
        settings.setTargetExam(request.targetExam());
        settings.setDailyNewWords(request.dailyNewWords());
        settings.setAiKeyMode(request.aiKeyMode());
        settings.setEnableDailyReport(request.enableDailyReport());
        settings.setTimezone(request.timezone().trim());
        userSettingsMapper.updateById(settings);
        userSettingsCacheService.put(settings);
        return settings;
    }

    /** 为指定用户创建默认设置（每日新词 30、PUBLIC 模式、Asia/Shanghai 时区） */
    private UserSettings createDefaultSettings(Long userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        settings.setTargetExam(null);
        settings.setDailyNewWords(30);
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        settings.setEnableDailyReport(true);
        settings.setTimezone("Asia/Shanghai");
        settings.setDeleted(0);
        userSettingsMapper.insert(settings);
        userSettingsCacheService.put(settings);
        return settings;
    }

    /** 将邮箱归一化：去除首尾空格并转为小写 */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
