package com.lexiflow.note.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.note.dto.StudyNoteQueryRequest;
import com.lexiflow.note.dto.StudyNoteRequest;
import com.lexiflow.note.dto.StudyNoteResponse;
import com.lexiflow.note.service.StudyNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "学习笔记接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notes")
public class StudyNoteController {

    private final StudyNoteService studyNoteService;

    @Operation(summary = "分页查询学习笔记")
    @GetMapping
    public ApiResponse<PageResponse<StudyNoteResponse>> pageNotes(@Valid @ModelAttribute StudyNoteQueryRequest request) {
        return ApiResponse.success(studyNoteService.pageNotes(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "查询学习笔记详情")
    @GetMapping("/{noteId}")
    public ApiResponse<StudyNoteResponse> getNote(@PathVariable @Positive Long noteId) {
        return ApiResponse.success(studyNoteService.getNote(AuthContext.currentUserId(), noteId));
    }

    @Operation(summary = "创建学习笔记")
    @PostMapping
    public ApiResponse<StudyNoteResponse> createNote(@Valid @RequestBody StudyNoteRequest request) {
        return ApiResponse.success(studyNoteService.createNote(AuthContext.currentUserId(), request));
    }

    @Operation(summary = "更新学习笔记")
    @PutMapping("/{noteId}")
    public ApiResponse<StudyNoteResponse> updateNote(
            @PathVariable @Positive Long noteId,
            @Valid @RequestBody StudyNoteRequest request
    ) {
        return ApiResponse.success(studyNoteService.updateNote(AuthContext.currentUserId(), noteId, request));
    }

    @Operation(summary = "删除学习笔记")
    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> deleteNote(@PathVariable @Positive Long noteId) {
        studyNoteService.deleteNote(AuthContext.currentUserId(), noteId);
        return ApiResponse.success();
    }
}
