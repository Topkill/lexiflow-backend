package com.lexiflow.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.admin.dto.AdminUserDetailResponse;
import com.lexiflow.admin.dto.AdminUserQueryRequest;
import com.lexiflow.admin.dto.AdminUserResponse;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.auth.service.AuthUserCacheService;
import com.lexiflow.auth.service.TokenVersionService;
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
    private final TokenVersionService tokenVersionService;
    /**
     * 分页查询管理员用户列表。
     * <p>
     * 根据查询请求中的关键词、状态等条件进行筛选，并按创建时间和ID降序排列。
     *
     * @param request 查询请求对象，包含分页参数、搜索关键词和用户状态。
     * @return 分页响应对象，包含管理员用户响应列表、总记录数、当前页码和每页大小。
     */
    public PageResponse<AdminUserResponse> pageUsers(AdminUserQueryRequest request) {
        // 构建查询条件：默认按创建时间和ID降序排列
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);

        // 如果存在关键词，则在邮箱或昵称中进行模糊匹配
        if (StringUtils.hasText(request.keyword())) {
            String keyword = request.keyword().trim();
            wrapper.and(query -> query.like(User::getEmail, keyword).or().like(User::getNickname, keyword));
        }

        // 如果指定了用户状态，则添加状态等于条件
        if (request.status() != null) {
            wrapper.eq(User::getStatus, request.status());
        }

        // 执行分页查询
        Page<User> page = userMapper.selectPage(Page.of(request.page(), request.size()), wrapper);

        // 将查询结果转换为响应对象并返回分页信息
        return PageResponse.of(
                page.getRecords().stream().map(AdminUserResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    /**
     * 获取指定用户的管理员详情信息，包含用户基础数据、学习事件数量及AI调用次数。
     *
     * @param userId 用户ID
     * @return 包含用户实体、学习事件统计数和AI调用统计数的管理员用户详情响应对象
     */
    public AdminUserDetailResponse getUser(Long userId) {
        // 获取用户基础实体信息
        User user = getUserEntity(userId);

        // 统计该用户的学习事件总数
        long studyEventCount = studyEventMapper.selectCount(new LambdaQueryWrapper<StudyEvent>()
                .eq(StudyEvent::getUserId, userId));

        // 统计该用户的AI调用日志总数
        long aiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, userId));

        return AdminUserDetailResponse.of(user, studyEventCount, aiCallCount);
    }

    /**
     * 停用指定业务数据。
     *
     * @param userId 用户ID
     */

    @Transactional
    public void disableUser(Long userId) {
        User user = getUserEntity(userId);
        // 校验是否为管理员账号，禁止禁用
        if (user.getRole() == UserRole.ADMIN) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能禁用管理员账号");
        }
        user.setStatus(UserStatus.DISABLED);
        userMapper.updateById(user);
        // 更新Token版本并清除认证缓存，确保下线生效
        tokenVersionService.bumpVersion(userId);
        authUserCacheService.evict(userId);
    }

    /**
     * 启用指定用户。
     * <p>
     * 该方法将用户状态更新为激活状态，并同步更新Token版本及清除用户认证缓存，以确保数据一致性和安全性。
     *
     * @param userId 用户ID
     */
    @Transactional
    public void enableUser(Long userId) {
        // 获取用户实体并更新状态为激活
        User user = getUserEntity(userId);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.updateById(user);

        // 递增Token版本号以使旧Token失效，并清除用户认证缓存
        tokenVersionService.bumpVersion(userId);
        authUserCacheService.evict(userId);
    }


        /**
     * 根据用户ID获取用户实体对象
     *
     * @param userId 用户ID
     * @return 用户实体对象
     * @throws BizException 当用户不存在时抛出业务异常
     */
    private User getUserEntity(Long userId) {
        // 通过用户ID查询用户信息，若不存在则抛出异常
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

}
