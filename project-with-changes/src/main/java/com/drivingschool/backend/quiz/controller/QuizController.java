package com.drivingschool.backend.quiz.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.quiz.dto.CreateQuizQuestionRequest;
import com.drivingschool.backend.quiz.dto.CreateQuizRequest;
import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.dto.QuizSubmissionResponse;
import com.drivingschool.backend.quiz.dto.SubmitQuizRequest;
import com.drivingschool.backend.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes", description = "Quiz creation, publishing, and submissions")
@SecurityRequirement(name = "Bearer Authentication")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping
    @Operation(summary = "Create a quiz for a course")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizResponse>> create(@Valid @RequestBody CreateQuizRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Quiz created", quizService.create(request, userId, role)));
    }

    @PostMapping("/{quizId}/questions")
    @Operation(summary = "Add a question to a quiz")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizResponse>> addQuestion(
            @PathVariable Long quizId,
            @Valid @RequestBody CreateQuizQuestionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(quizService.addQuestion(quizId, request, userId, role)));
    }

    @PutMapping("/{quizId}/publish")
    @Operation(summary = "Publish a quiz")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<QuizResponse>> publish(@PathVariable Long quizId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success("Quiz published", quizService.publish(quizId, userId, role)));
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get quiz by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<QuizResponse>> getById(
            @PathVariable Long quizId,
            @RequestParam(defaultValue = "true") boolean forStudent) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(quizService.getById(quizId, forStudent, userId, role)));
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "List published quizzes for a course")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<QuizResponse>>> getByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success(quizService.getPublishedByCourse(courseId)));
    }

    @PostMapping("/{quizId}/submit")
    @Operation(summary = "Submit quiz answers for grading")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<ApiResponse<QuizSubmissionResponse>> submit(
            @PathVariable Long quizId,
            @Valid @RequestBody SubmitQuizRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz submitted", quizService.submit(quizId, request, userId, role)));
    }
}
