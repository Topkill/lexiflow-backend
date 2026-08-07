package com.lexiflow.review.controller;

import com.lexiflow.auth.security.AuthContext;
import com.lexiflow.common.api.ApiResponse;
import com.lexiflow.common.api.PageResponse;
import com.lexiflow.review.dto.FavoriteWordRequest;
import com.lexiflow.review.dto.FavoriteWordResponse;
import com.lexiflow.review.dto.ReviewQueryRequest;
import com.lexiflow.review.dto.ReviewWordResponse;
import com.lexiflow.review.dto.WrongWordResponse;
import com.lexiflow.review.service.ReviewService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复习错词收藏接口控制器。
 * <p>提供到期复习词查询、错词本管理、收藏词管理等功能。</p>
 */
@Tag(name = "复习错词收藏接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/review")
public class ReviewController {

    private final ReviewService reviewService;

    /** 查询到期复习词列表。 */
    @Operation(summary = "查询到期复习词")
    @GetMapping("/due-words")
    public ApiResponse<PageResponse<ReviewWordResponse>> dueWords(@Valid @ModelAttribute ReviewQueryRequest request) {
        return ApiResponse.success(reviewService.pageDueWords(AuthContext.currentUserId(), request));
    }

    /** 查询错词本列表。 */
    @Operation(summary = "查询错词本")
    @GetMapping("/wrong-words")
    public ApiResponse<PageResponse<WrongWordResponse>> wrongWords(@Valid @ModelAttribute ReviewQueryRequest request) {
        return ApiResponse.success(reviewService.pageWrongWords(AuthContext.currentUserId(), request));
    }

    /** 标记指定错词为已解决。 */
    @Operation(summary = "标记错词已解决")
    @PostMapping("/wrong-words/{wrongWordId}/resolve")
    public ApiResponse<Void> resolveWrongWord(@PathVariable @Positive Long wrongWordId) {
        reviewService.resolveWrongWord(AuthContext.currentUserId(), wrongWordId);
        return ApiResponse.success();
    }

    /** 查询收藏词列表。 */
    @Operation(summary = "查询收藏词")
    @GetMapping("/favorite-words")
    public ApiResponse<PageResponse<FavoriteWordResponse>> favoriteWords(@Valid @ModelAttribute ReviewQueryRequest request) {
        return ApiResponse.success(reviewService.pageFavoriteWords(AuthContext.currentUserId(), request));
    }

    /** 收藏单词，若已收藏则更新备注。 */
    @Operation(summary = "收藏单词")
    @PostMapping("/favorite-words")
    public ApiResponse<FavoriteWordResponse> favoriteWord(@Valid @RequestBody FavoriteWordRequest request) {
        return ApiResponse.success(reviewService.favoriteWord(AuthContext.currentUserId(), request));
    }

    /** 取消收藏指定单词。 */
    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorite-words/{favoriteWordId}")
    public ApiResponse<Void> deleteFavoriteWord(@PathVariable @Positive Long favoriteWordId) {
        reviewService.deleteFavoriteWord(AuthContext.currentUserId(), favoriteWordId);
        return ApiResponse.success();
    }
}
