package com.lexiflow.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private StudyEventMapper studyEventMapper;
    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private AuthUserCacheService authUserCacheService;

    @Test
    void disableUserShouldEvictAuthUserCache() {
        AdminUserService service = adminUserService();
        User user = user(UserStatus.ACTIVE);
        when(userMapper.selectById(7L)).thenReturn(user);

        service.disableUser(7L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(userMapper).updateById(user);
        verify(authUserCacheService).evict(7L);
    }

    @Test
    void enableUserShouldEvictAuthUserCache() {
        AdminUserService service = adminUserService();
        User user = user(UserStatus.DISABLED);
        when(userMapper.selectById(7L)).thenReturn(user);

        service.enableUser(7L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userMapper).updateById(user);
        verify(authUserCacheService).evict(7L);
    }

    private AdminUserService adminUserService() {
        return new AdminUserService(userMapper, studyEventMapper, aiCallLogMapper, authUserCacheService);
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setId(7L);
        user.setEmail("student@example.com");
        user.setNickname("Student");
        user.setRole(UserRole.USER);
        user.setStatus(status);
        return user;
    }
}
