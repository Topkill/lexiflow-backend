package com.lexiflow.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexiflow.ai.content.domain.AiContentType;
import com.lexiflow.ai.core.dto.AiChatCompletionResult;
import com.lexiflow.ai.core.dto.AiPrompt;
import com.lexiflow.ai.core.service.AiGatewayService;
import com.lexiflow.ai.core.util.AiJsonUtils;
import com.lexiflow.async.domain.AsyncTask;
import com.lexiflow.async.domain.AsyncTaskType;
import com.lexiflow.async.service.AsyncTaskService;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.quiz.cloze.domain.ClozeAttempt;
import com.lexiflow.quiz.cloze.mapper.ClozeAttemptMapper;
import com.lexiflow.report.domain.StudyReport;
import com.lexiflow.report.dto.CreateReportTaskRequest;
import com.lexiflow.report.dto.CreateReportTaskResponse;
import com.lexiflow.report.dto.ReportQueryRequest;
import com.lexiflow.report.dto.StudyReportResponse;
import com.lexiflow.report.mapper.StudyReportMapper;
import com.lexiflow.study.domain.StudyPlan;
import com.lexiflow.study.mapper.StudyPlanMapper;
import com.lexiflow.study.progress.domain.StudyEvent;
import com.lexiflow.study.progress.domain.StudyScene;
import com.lexiflow.study.progress.domain.WrongWord;
import com.lexiflow.study.progress.mapper.StudyEventMapper;
import com.lexiflow.study.progress.mapper.WrongWordMapper;
import com.lexiflow.study.task.domain.DailyTask;
import com.lexiflow.study.task.mapper.DailyTaskMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudyReportService {

    private static final String SYSTEM_PROMPT = "你是 LexiFlow 的 AI 学习教练。请只输出合法 JSON，不要输出 Markdown 代码块。总结要具体、鼓励但不夸张，面向备考大学生。";

    private final AsyncTaskService asyncTaskService;
    private final AiGatewayService aiGatewayService;
    private final StudyReportMapper studyReportMapper;
    private final DailyTaskMapper dailyTaskMapper;
    private final StudyPlanMapper studyPlanMapper;
    private final StudyEventMapper studyEventMapper;
    private final WrongWordMapper wrongWordMapper;
    private final ClozeAttemptMapper clozeAttemptMapper;
    private final ObjectMapper objectMapper;

    public CreateReportTaskResponse createReportTask(Long userId, CreateReportTaskRequest request) {
        DailyTask dailyTask = getOwnedDailyTask(userId, request.dailyTaskId());
        String requestJson = toJson(Map.of(
                "dailyTaskId", String.valueOf(request.dailyTaskId()),
                "reportDate", request.reportDate().toString()
        ));
        AsyncTask task = asyncTaskService.createTask(userId, AsyncTaskType.AI_REPORT, requestJson);
        try {
            asyncTaskService.markRunning(task.getId(), "正在生成学习报告", 20);
            StudyReport report = generateReport(userId, dailyTask, task.getId(), request.reportDate());
            asyncTaskService.markSuccess(task.getId(), report.getId(), "学习报告生成完成");
            return CreateReportTaskResponse.from(asyncTaskService.getOwnedTaskEntity(userId, task.getId()));
        } catch (BizException ex) {
            asyncTaskService.markFailed(task.getId(), String.valueOf(ex.getErrorCode().getCode()), ex.getCustomMessage());
            throw ex;
        } catch (Exception ex) {
            asyncTaskService.markFailed(task.getId(), String.valueOf(ErrorCode.ASYNC_TASK_FAILED.getCode()), ex.getMessage());
            throw new BizException(ErrorCode.ASYNC_TASK_FAILED, "学习报告生成失败，请稍后重试");
        }
    }

    public StudyReportResponse getReport(Long userId, Long reportId) {
        StudyReport report = studyReportMapper.selectOne(new LambdaQueryWrapper<StudyReport>()
                .eq(StudyReport::getId, reportId)
                .eq(StudyReport::getUserId, userId)
                .last("LIMIT 1"));
        if (report == null) {
            throw new BizException(ErrorCode.STUDY_REPORT_NOT_FOUND);
        }
        return StudyReportResponse.of(report, parseJsonNode(report.getSummaryJson()));
    }

    public PageResponse<StudyReportResponse> pageReports(Long userId, ReportQueryRequest request) {
        ReportQueryRequest safeRequest = request == null ? new ReportQueryRequest(null, null, null, null) : request;
        LambdaQueryWrapper<StudyReport> wrapper = new LambdaQueryWrapper<StudyReport>()
                .eq(StudyReport::getUserId, userId)
                .orderByDesc(StudyReport::getReportDate)
                .orderByDesc(StudyReport::getId);
        if (safeRequest.startDate() != null) {
            wrapper.ge(StudyReport::getReportDate, safeRequest.startDate());
        }
        if (safeRequest.endDate() != null) {
            wrapper.le(StudyReport::getReportDate, safeRequest.endDate());
        }
        Page<StudyReport> page = studyReportMapper.selectPage(Page.of(safeRequest.safePage(), safeRequest.safeSize()), wrapper);
        List<StudyReportResponse> records = page.getRecords().stream()
                .map(report -> StudyReportResponse.of(report, parseJsonNode(report.getSummaryJson())))
                .toList();
        return PageResponse.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    protected StudyReport generateReport(Long userId, DailyTask dailyTask, Long asyncTaskId, LocalDate reportDate) {
        StudyPlan plan = studyPlanMapper.selectById(dailyTask.getPlanId());
        if (plan == null) {
            throw new BizException(ErrorCode.STUDY_PLAN_NOT_FOUND);
        }
        ReportStats stats = buildStats(userId, dailyTask, plan, reportDate);
        AiPrompt prompt = buildPrompt(stats);
        AiChatCompletionResult result = aiGatewayService.generateJson(userId, AiContentType.REPORT, prompt);
        JsonNode content = parseJson(result.content());
        return upsertReport(userId, dailyTask, plan, asyncTaskId, reportDate, stats, content);
    }

    private ReportStats buildStats(Long userId, DailyTask dailyTask, StudyPlan plan, LocalDate reportDate) {
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end = reportDate.plusDays(1).atStartOfDay();
        Long correctEvents = studyEventMapper.selectCount(new LambdaQueryWrapper<StudyEvent>()
                .eq(StudyEvent::getUserId, userId)
                .eq(StudyEvent::getWordbookId, plan.getWordbookId())
                .ge(StudyEvent::getCreatedAt, start)
                .lt(StudyEvent::getCreatedAt, end)
                .eq(StudyEvent::getIsCorrect, true));
        Long wrongEvents = studyEventMapper.selectCount(new LambdaQueryWrapper<StudyEvent>()
                .eq(StudyEvent::getUserId, userId)
                .eq(StudyEvent::getWordbookId, plan.getWordbookId())
                .ge(StudyEvent::getCreatedAt, start)
                .lt(StudyEvent::getCreatedAt, end)
                .eq(StudyEvent::getIsCorrect, false));
        List<WrongWord> wrongWords = wrongWordMapper.selectList(new LambdaQueryWrapper<WrongWord>()
                .eq(WrongWord::getUserId, userId)
                .eq(WrongWord::getWordbookId, plan.getWordbookId())
                .eq(WrongWord::getResolved, false)
                .orderByDesc(WrongWord::getWrongCount)
                .orderByDesc(WrongWord::getLastWrongAt)
                .last("LIMIT 10"));
        List<ClozeAttempt> attempts = clozeAttemptMapper.selectList(new LambdaQueryWrapper<ClozeAttempt>()
                .eq(ClozeAttempt::getUserId, userId)
                .eq(ClozeAttempt::getWordbookId, plan.getWordbookId())
                .ge(ClozeAttempt::getSubmittedAt, start)
                .lt(ClozeAttempt::getSubmittedAt, end));
        BigDecimal quizAccuracy = calculateQuizAccuracy(attempts);
        return new ReportStats(
                dailyTask.getId(),
                plan.getId(),
                plan.getWordbookId(),
                reportDate,
                dailyTask.getNewCount(),
                dailyTask.getReviewCount(),
                correctEvents.intValue(),
                wrongEvents.intValue(),
                quizAccuracy,
                wrongWords.stream().map(WrongWord::getWordId).map(String::valueOf).toList()
        );
    }

    private AiPrompt buildPrompt(ReportStats stats) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reportDate", stats.reportDate().toString());
        context.put("dailyTaskId", String.valueOf(stats.dailyTaskId()));
        context.put("wordbookId", String.valueOf(stats.wordbookId()));
        context.put("newWordsCount", stats.newWordsCount());
        context.put("reviewWordsCount", stats.reviewWordsCount());
        context.put("correctEvents", stats.correctEvents());
        context.put("wrongEvents", stats.wrongEvents());
        context.put("quizAccuracy", stats.quizAccuracy());
        context.put("wrongWordIds", stats.wrongWordIds());
        String sourceJson = toJson(context);
        String schema = "输出 JSON 对象：summary 对象，包含 performance、weakness、tomorrowAdvice 三个中文字符串；markdownContent 字符串，使用 Markdown 写一段日报，包含今日完成、正确率、错词提醒和明日建议。";
        String userPrompt = schema + "\n请基于以下学习数据生成日报，不要编造具体单词文本。\n" + sourceJson;
        return new AiPrompt(SYSTEM_PROMPT, userPrompt, sha256(sourceJson));
    }

    private StudyReport upsertReport(Long userId, DailyTask dailyTask, StudyPlan plan, Long asyncTaskId, LocalDate reportDate, ReportStats stats, JsonNode content) {
        StudyReport report = studyReportMapper.selectOne(new LambdaQueryWrapper<StudyReport>()
                .eq(StudyReport::getUserId, userId)
                .eq(StudyReport::getReportDate, reportDate)
                .last("LIMIT 1"));
        if (report == null) {
            report = new StudyReport();
            report.setUserId(userId);
            report.setReportDate(reportDate);
            report.setDeleted(0);
            report.setVersion(0);
        }
        report.setPlanId(plan.getId());
        report.setWordbookId(plan.getWordbookId());
        report.setDailyTaskId(dailyTask.getId());
        report.setAsyncTaskId(asyncTaskId);
        report.setNewWordsCount(stats.newWordsCount());
        report.setReviewWordsCount(stats.reviewWordsCount());
        report.setQuizAccuracy(stats.quizAccuracy());
        report.setWrongWordIds(toJson(stats.wrongWordIds()));
        report.setSummaryJson(toJson(content.path("summary")));
        report.setMarkdownContent(content.path("markdownContent").asText(defaultMarkdown(stats)));
        report.setModelName(null);
        if (report.getId() == null) {
            studyReportMapper.insert(report);
        } else {
            studyReportMapper.updateById(report);
        }
        return report;
    }

    private DailyTask getOwnedDailyTask(Long userId, Long dailyTaskId) {
        DailyTask task = dailyTaskMapper.selectOne(new LambdaQueryWrapper<DailyTask>()
                .eq(DailyTask::getId, dailyTaskId)
                .eq(DailyTask::getUserId, userId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BizException(ErrorCode.TODAY_TASK_NOT_FOUND);
        }
        return task;
    }

    private BigDecimal calculateQuizAccuracy(List<ClozeAttempt> attempts) {
        int total = attempts.stream().mapToInt(ClozeAttempt::getTotalBlanks).sum();
        int correct = attempts.stream().mapToInt(ClozeAttempt::getCorrectCount).sum();
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(correct)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private JsonNode parseJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private JsonNode parseJson(String json) {
        return AiJsonUtils.parseObject(objectMapper, json);
    }

    private String defaultMarkdown(ReportStats stats) {
        return "## 今日学习报告\n\n"
                + "- 今日新词：" + stats.newWordsCount() + "\n"
                + "- 今日复习：" + stats.reviewWordsCount() + "\n"
                + "- 测验正确率：" + (stats.quizAccuracy() == null ? "暂无测验" : stats.quizAccuracy() + "%") + "\n";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private record ReportStats(
            Long dailyTaskId,
            Long planId,
            Long wordbookId,
            LocalDate reportDate,
            Integer newWordsCount,
            Integer reviewWordsCount,
            Integer correctEvents,
            Integer wrongEvents,
            BigDecimal quizAccuracy,
            List<String> wrongWordIds
    ) {
    }
}
