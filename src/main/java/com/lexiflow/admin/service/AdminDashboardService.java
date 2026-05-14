package com.lexiflow.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lexiflow.admin.dto.AdminOverviewResponse;
import com.lexiflow.ai.content.domain.AiCallLog;
import com.lexiflow.ai.content.domain.AiCallStatus;
import com.lexiflow.ai.content.mapper.AiCallLogMapper;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.user.domain.User;
import com.lexiflow.user.domain.UserStatus;
import com.lexiflow.user.mapper.UserMapper;
import com.lexiflow.wordbook.domain.Word;
import com.lexiflow.wordbook.domain.Wordbook;
import com.lexiflow.wordbook.mapper.WordMapper;
import com.lexiflow.wordbook.mapper.WordbookMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserMapper userMapper;
    private final WordbookMapper wordbookMapper;
    private final WordMapper wordMapper;
    private final StudyEventMapper studyEventMapper;
    private final AiCallLogMapper aiCallLogMapper;

    public AdminOverviewResponse overview() {
        LocalDate today = LocalDate.now();
        long registeredUsers = userMapper.selectCount(new LambdaQueryWrapper<User>());
        long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatus.ACTIVE));
        long todayLearners = studyEventMapper.selectList(new LambdaQueryWrapper<StudyEvent>()
                        .select(StudyEvent::getUserId)
                        .ge(StudyEvent::getCreatedAt, today.atStartOfDay())
                        .lt(StudyEvent::getCreatedAt, today.plusDays(1).atStartOfDay()))
                .stream()
                .map(StudyEvent::getUserId)
                .distinct()
                .count();
        long wordbookCount = wordbookMapper.selectCount(new LambdaQueryWrapper<Wordbook>());
        long wordCount = wordMapper.selectCount(new LambdaQueryWrapper<Word>());
        long aiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>());
        long aiSuccessCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getStatus, AiCallStatus.SUCCESS));
        BigDecimal aiSuccessRate = aiCallCount == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(aiSuccessCount)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(aiCallCount), 2, RoundingMode.HALF_UP);
        long todayAiCallCount = aiCallLogMapper.selectCount(new LambdaQueryWrapper<AiCallLog>()
                .ge(AiCallLog::getCreatedAt, today.atStartOfDay())
                .lt(AiCallLog::getCreatedAt, today.plusDays(1).atStartOfDay()));
        return new AdminOverviewResponse(
                registeredUsers,
                activeUsers,
                todayLearners,
                wordbookCount,
                wordCount,
                aiCallCount,
                aiSuccessRate,
                todayAiCallCount
        );
    }
}