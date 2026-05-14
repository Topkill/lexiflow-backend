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

    public PageResponse<AdminAiCallLogResponse> pageLogs(AdminAiCallLogQueryRequest request) {
        AdminAiCallLogQueryRequest safeRequest = request == null
                ? new AdminAiCallLogQueryRequest(null, null, null, null, null, null, null, null)
                : request;
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<AiCallLog>()
                .orderByDesc(AiCallLog::getCreatedAt)
                .orderByDesc(AiCallLog::getId);
        if (safeRequest.userId() != null) {
            wrapper.eq(AiCallLog::getUserId, safeRequest.userId());
        }
        if (safeRequest.configScope() != null) {
            wrapper.eq(AiCallLog::getConfigScope, safeRequest.configScope());
        }
        if (safeRequest.contentType() != null) {
            wrapper.eq(AiCallLog::getContentType, safeRequest.contentType());
        }
        if (safeRequest.status() != null) {
            wrapper.eq(AiCallLog::getStatus, safeRequest.status());
        }
        if (safeRequest.startDate() != null) {
            wrapper.ge(AiCallLog::getCreatedAt, safeRequest.startDate().atStartOfDay());
        }
        if (safeRequest.endDate() != null) {
            wrapper.lt(AiCallLog::getCreatedAt, safeRequest.endDate().plusDays(1).atStartOfDay());
        }
        Page<AiCallLog> page = aiCallLogMapper.selectPage(Page.of(safeRequest.safePage(), safeRequest.safeSize()), wrapper);
        return PageResponse.of(
                page.getRecords().stream().map(AdminAiCallLogResponse::from).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }
}