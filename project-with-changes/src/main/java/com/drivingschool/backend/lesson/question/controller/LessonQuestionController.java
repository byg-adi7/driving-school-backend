package com.drivingschool.backend.lesson.question.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.lesson.question.dto.QuestionResponse;
import com.drivingschool.backend.lesson.question.dto.RespondToQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.StatusHistoryResponse;
import com.drivingschool.backend.lesson.question.dto.SubmitQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.UpdateQuestionStatusRequest;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.lesson.question.service.LessonQuestionStatusHistoryService;
import com.drivingschool.backend.lesson.question.service.LessonQuestionSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lesson-questions")
@Tag(name = "Lesson Questions", description = "Manage student questions related to lessons")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class LessonQuestionController {

    private final LessonQuestionSubmissionService questionService;
    private final LessonQuestionStatusHistoryService statusHistoryService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit lesson question", description = "Students can submit questions about lessons")
    public ResponseEntity<ApiResponse<QuestionResponse>> submitQuestion(
            @Valid @RequestBody SubmitQuestionRequest request
    ) {
        Long studentId = SecurityUtils.getCurrentUserId();
        QuestionResponse response = questionService.submitQuestion(request, studentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success( "Question submitted successfully",response));
    }

    @PostMapping("/{id}/respond")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Respond to question", description = "Instructors can respond to student questions")
    public ResponseEntity<ApiResponse<QuestionResponse>> respondToQuestion(
            @PathVariable Long id,
            @Valid @RequestBody RespondToQuestionRequest request
    ) {
        Long instructorId = SecurityUtils.getCurrentUserId();
        QuestionResponse response = questionService.respondToQuestion(id, request, instructorId);
        return ResponseEntity.ok(ApiResponse.success( "Response submitted successfully",response));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Update question status", description = "Update the status of a question")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestionStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuestionStatusRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        QuestionResponse response = questionService.updateQuestionStatus(id, request, userId, role);
        return ResponseEntity.ok(ApiResponse.success( "Question status updated successfully",response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get question details", description = "Get details of a specific question")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestion(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        QuestionResponse response = questionService.getQuestion(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-questions")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my questions", description = "Get all questions submitted by the current student")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getMyQuestions(Pageable pageable) {
        Long studentId = SecurityUtils.getCurrentUserId();
        Page<QuestionResponse> questions = questionService.getStudentQuestions(studentId, pageable);
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Get assigned questions", description = "Get questions assigned to the current instructor")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getAssignedQuestions(Pageable pageable) {
        Long instructorId = SecurityUtils.getCurrentUserId();
        Page<QuestionResponse> questions = questionService.getInstructorQuestions(instructorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get questions by status", description = "Get questions filtered by status")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getQuestionsByStatus(
            @PathVariable QuestionStatus status,
            Pageable pageable
    ) {
        Page<QuestionResponse> questions = questionService.getQuestionsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Get pending questions", description = "Get pending questions for the current instructor")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> getPendingQuestions(Pageable pageable) {
        Long instructorId = SecurityUtils.getCurrentUserId();
        Page<QuestionResponse> questions = questionService.getInstructorPendingQuestions(instructorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get status history", description = "Get status change history for a question")
    public ResponseEntity<ApiResponse<List<StatusHistoryResponse>>> getStatusHistory(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        List<StatusHistoryResponse> history = statusHistoryService.getQuestionStatusHistory(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{id}/history/paginated")
    @Operation(summary = "Get status history paginated", description = "Get status change history with pagination")
    public ResponseEntity<ApiResponse<Page<StatusHistoryResponse>>> getStatusHistoryPaginated(
            @PathVariable Long id,
            Pageable pageable
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        Page<StatusHistoryResponse> history = statusHistoryService.getQuestionStatusHistoryPaginated(id, pageable, userId, role);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
