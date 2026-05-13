package com.lexiflow.ai.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.ai.core.dto.AiConfigScope;
import com.lexiflow.ai.core.dto.AiRuntimeConfig;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private final AiCallLogMapper aiCallLogMapper;

    public void checkQuota(Long userId, AiRuntimeConfig config) {
        if (config.scope() != AiConfigScope.PUBLIC) {
            return;
        }
        int quota = config.dailyQuotaPerUser() == null ? 0 : config.dailyQuotaPerUser();
        if (quota <= 0) {
            throw new BizException(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
        }
        LocalDate today = LocalDate.now();
        Long used = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, userId)
                .eq(AiCallLog::getConfigScope, AiConfigScope.PUBLIC)
                .ge(AiCallLog::getCreatedAt, today.atStartOfDay())
                .lt(AiCallLog::getCreatedAt, today.plusDays(1).atStartOfDay()));
        if (used >= quota) {
            throw new BizException(ErrorCode.AI_PUBLIC_QUOTA_EXHAUSTED);
        }
    }
}