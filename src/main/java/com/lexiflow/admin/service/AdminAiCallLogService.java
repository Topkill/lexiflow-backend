package com.lexiflow.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lexiflow.admin.dto.AdminAiCallLogQueryRequest;
import com.lexiflow.admin.dto.AdminAiCallLogResponse;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAiCallLogService {

    private final AiCallLogMapper aiCallLogMapper;
    /**
     * 分页查询AI调用日志数据。
     * <p>
     * 根据查询请求中的条件（用户ID、配置范围、内容类型、状态、时间范围等）进行过滤，
     * 并按创建时间和ID降序排列返回分页结果。
     * </p>
     *
     * @param request 查询请求对象，包含分页参数及过滤条件。
     * @return 分页响应对象，包含转换后的日志记录列表、总记录数、当前页码和每页大小。
     */
    public PageResponse<AdminAiCallLogResponse> pageLogs(AdminAiCallLogQueryRequest request) {
        // 构建查询条件，默认按创建时间和ID降序排列
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<AiCallLog>()
                .orderByDesc(AiCallLog::getCreatedAt)
                .orderByDesc(AiCallLog::getId);

        // 根据请求中的非空字段动态添加查询条件
        if (request.userId() != null) {
            wrapper.eq(AiCallLog::getUserId, request.userId());
        }
        if (request.configScope() != null) {
            wrapper.eq(AiCallLog::getConfigScope, request.configScope());
        }
        if (request.contentType() != null) {
            wrapper.eq(AiCallLog::getContentType, request.contentType());
        }
        if (request.status() != null) {
            wrapper.eq(AiCallLog::getStatus, request.status());
        }

        // 处理时间范围查询：起始时间包含当天零点，结束时间不包含次日零点（即包含结束日期全天）
        if (request.startDate() != null) {
            wrapper.ge(AiCallLog::getCreatedAt, request.startDate().atStartOfDay());
        }
        if (request.endDate() != null) {
            wrapper.lt(AiCallLog::getCreatedAt, request.endDate().plusDays(1).atStartOfDay());
        }

        // 执行分页查询
        Page<AiCallLog> page = aiCallLogMapper.selectPage(Page.of(request.page(), request.size()), wrapper);

        // 将实体对象转换为响应DTO，并构建分页响应结果
        return PageResponse.of(
                page.getRecords().stream().map(AdminAiCallLogResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

}
