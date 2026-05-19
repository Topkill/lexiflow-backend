package com.lexiflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserSettingsMapper userSettingsMapper;
    private final PasswordEncoder passwordEncoder;

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

    public User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, normalizeEmail(email))
                .last("LIMIT 1"));
    }

    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public User getActiveUserById(Long userId) {
        User user = getById(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return user;
    }

    public void updateLoginInfo(Long userId, String clientIp) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
    }

    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getActiveUserById(userId);
        user.setNickname(request.nickname().trim());
        user.setAvatarUrl(StringUtils.hasText(request.avatarUrl()) ? request.avatarUrl().trim() : null);
        userMapper.updateById(user);
        return getActiveUserById(userId);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getActiveUserById(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    public UserSettings getOrCreateSettings(Long userId) {
        UserSettings settings = userSettingsMapper.selectOne(new LambdaQueryWrapper<UserSettings>()
                .eq(UserSettings::getUserId, userId)
                .last("LIMIT 1"));
        if (settings != null) {
            return settings;
        }
        return createDefaultSettings(userId);
    }

    public UserSettings updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSettings settings = getOrCreateSettings(userId);
        settings.setTargetExam(request.targetExam());
        settings.setDailyNewWords(request.dailyNewWords());
        settings.setAiKeyMode(request.aiKeyMode());
        settings.setEnableDailyReport(request.enableDailyReport());
        settings.setTimezone(request.timezone().trim());
        userSettingsMapper.updateById(settings);
        return getOrCreateSettings(userId);
    }

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
        return settings;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
