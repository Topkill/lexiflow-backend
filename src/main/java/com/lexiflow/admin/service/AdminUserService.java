package com.lexiflow.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.admin.dto.AdminUserDetailResponse;
import com.lexiflow.admin.dto.AdminUserQueryRequest;
import com.lexiflow.admin.dto.AdminUserResponse;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserRole;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final StudyEventMapper studyEventMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AuthUserCacheService authUserCacheService;

    public PageResponse<AdminUserResponse> pageUsers(AdminUserQueryRequest request) {
        AdminUserQueryRequest safeRequest = request == null ? new AdminUserQueryRequest(null, null, null, null) : request;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);
        if (StringUtils.hasText(safeRequest.keyword())) {
            String keyword = safeRequest.keyword().trim();
            wrapper.and(query -> query.like(User::getEmail, keyword).or().like(User::getNickname, keyword));
        }
        if (safeRequest.status() != null) {
            wrapper.eq(User::getStatus, safeRequest.status());
        }
        Page<User> page = userMapper.selectPage(Page.of(safeRequest.safePage(), safeRequest.safeSize()), wrapper);
        return PageResponse.of(
                page.getRecords().stream().map(AdminUserResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    public AdminUserDetailResponse getUser(Long userId) {
        User user = getUserEntity(userId);
        long studyEventCount = studyEventMapper.selectCount(new LambdaQueryWrapper<StudyEvent>()
                .eq(StudyEvent::getUserId, userId));
        long aiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, userId));
        return AdminUserDetailResponse.of(user, studyEventCount, aiCallCount);
    }

    @Transactional
    public void disableUser(Long userId) {
        User user = getUserEntity(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能禁用管理员账号");
        }
        user.setStatus(UserStatus.DISABLED);
        userMapper.updateById(user);
        authUserCacheService.evict(userId);
    }

    @Transactional
    public void enableUser(Long userId) {
        User user = getUserEntity(userId);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.updateById(user);
        authUserCacheService.evict(userId);
    }

    private User getUserEntity(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
