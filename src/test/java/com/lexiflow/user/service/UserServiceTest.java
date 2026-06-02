package com.lexiflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.auth.service.TokenVersionService;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.dto.ChangePasswordRequest;
import com.lexiflow.user.dto.UpdateProfileRequest;
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

    private UserService userService() {
        return new UserService(userMapper, userSettingsMapper, passwordEncoder, authUserCacheService, tokenVersionService);
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
}
