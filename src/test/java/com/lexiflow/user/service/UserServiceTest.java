package com.lexiflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.auth.service.TokenVersionService;
import com.lexiflow.user.domain.AiKeyMode;
import com.lexiflow.user.domain.TargetExam;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserSettings;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.dto.ChangePasswordRequest;
import com.lexiflow.user.dto.UpdateProfileRequest;
import com.lexiflow.user.dto.UpdateUserSettingsRequest;
import com.lexiflow.user.mapper.UserMapper;
import com.lexiflow.user.mapper.UserSettingsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthUserCacheService authUserCacheService;
    @Mock
    private TokenVersionService tokenVersionService;
    @Mock
    private UserSettingsCacheService userSettingsCacheService;

    @Test
    void updateProfileShouldEvictAuthUserCache() {
        UserService service = userService();
        User user = activeUser();
        when(userMapper.selectById(7L)).thenReturn(user);

        User updatedUser = service.updateProfile(7L, new UpdateProfileRequest("New Name", "https://example.com/avatar.png"));

        assertThat(updatedUser.getNickname()).isEqualTo("New Name");
        assertThat(updatedUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(userMapper).updateById(user);
        verify(authUserCacheService).evict(7L);
    }

    @Test
    void changePasswordShouldBumpTokenVersion() {
        UserService service = userService();
        User user = activeUser();
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.matches("OldPassword123!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("new-hash");

        service.changePassword(7L, new ChangePasswordRequest("OldPassword123!", "NewPassword123!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userMapper).updateById(user);
        verify(tokenVersionService).bumpVersion(7L);
    }

    @Test
    void changePasswordShouldRunInTransaction() throws NoSuchMethodException {
        var method = UserService.class.getMethod("changePassword", Long.class, ChangePasswordRequest.class);

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void getOrCreateSettingsShouldReturnRedisCacheHitWithoutQueryingDatabase() {
        UserService service = userService();
        UserSettings cached = userSettings();
        when(userSettingsCacheService.get(7L)).thenReturn(cached);

        UserSettings settings = service.getOrCreateSettings(7L);

        assertThat(settings).isSameAs(cached);
        verify(userSettingsMapper, never()).selectOne(any());
    }

    @Test
    void getOrCreateSettingsShouldCacheDatabaseResult() {
        UserService service = userService();
        UserSettings settings = userSettings();
        when(userSettingsCacheService.get(7L)).thenReturn(null);
        when(userSettingsMapper.selectOne(any())).thenReturn(settings);

        UserSettings result = service.getOrCreateSettings(7L);

        assertThat(result).isSameAs(settings);
        verify(userSettingsCacheService).put(settings);
    }

    @Test
    void updateSettingsShouldRefreshUserSettingsCacheAfterDatabaseUpdate() {
        UserService service = userService();
        UserSettings settings = userSettings();
        when(userSettingsCacheService.get(7L)).thenReturn(settings);

        UserSettings updated = service.updateSettings(
                7L,
                new UpdateUserSettingsRequest(TargetExam.CET6, 50, AiKeyMode.PRIVATE, false, "Asia/Shanghai")
        );

        assertThat(updated.getTargetExam()).isEqualTo(TargetExam.CET6);
        assertThat(updated.getDailyNewWords()).isEqualTo(50);
        assertThat(updated.getAiKeyMode()).isEqualTo(AiKeyMode.PRIVATE);
        assertThat(updated.getEnableDailyReport()).isFalse();
        verify(userSettingsMapper).updateById(settings);
        verify(userSettingsCacheService).put(settings);
    }

    private UserService userService() {
        return new UserService(userMapper, userSettingsMapper, passwordEncoder, authUserCacheService, tokenVersionService, userSettingsCacheService);
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setEmail("student@example.com");
        user.setPasswordHash("old-hash");
        user.setNickname("Student");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private UserSettings userSettings() {
        UserSettings settings = new UserSettings();
        settings.setId(11L);
        settings.setUserId(7L);
        settings.setTargetExam(TargetExam.CET4);
        settings.setDailyNewWords(30);
        settings.setAiKeyMode(AiKeyMode.PUBLIC);
        settings.setEnableDailyReport(true);
        settings.setTimezone("Asia/Shanghai");
        settings.setDeleted(0);
        return settings;
    }
}
