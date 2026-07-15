package com.drivingschool.backend.lesson.question.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.lesson.question.dto.StatusHistoryResponse;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionStatusHistory;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionStatusHistoryRepository;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionSubmissionRepository;
import com.drivingschool.backend.lesson.question.validator.QuestionValidator;
import com.drivingschool.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonQuestionStatusHistoryService {

    private final LessonQuestionStatusHistoryRepository statusHistoryRepository;
    private final LessonQuestionSubmissionRepository questionRepository;
    private final QuestionValidator validator;

    @Transactional
    public void recordStatusChange(
            LessonQuestionSubmission question,
            QuestionStatus previousStatus,
            QuestionStatus newStatus,
            User changedBy,
            String changeReason
    ) {
        LessonQuestionStatusHistory history = LessonQuestionStatusHistory.builder()
                .questionSubmission(question)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .changeReason(changeReason)
                .build();

        statusHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getQuestionStatusHistory(Long questionId, Long userId, String role) {
        LessonQuestionSubmission question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));
        validator.validateReadAccess(question, userId, role);

        List<LessonQuestionStatusHistory> history = statusHistoryRepository.findByQuestionSubmissionId(questionId);
        return history.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<StatusHistoryResponse> getQuestionStatusHistoryPaginated(Long questionId, Pageable pageable, Long userId, String role) {
        LessonQuestionSubmission question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));
        validator.validateReadAccess(question, userId, role);

        Page<LessonQuestionStatusHistory> history = statusHistoryRepository.findByQuestionSubmissionIdPaginated(questionId, pageable);
        return history.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<StatusHistoryResponse> getStatusHistoryByUser(Long userId, Pageable pageable) {
        Page<LessonQuestionStatusHistory> history = statusHistoryRepository.findByChangedById(userId, pageable);
        return history.map(this::mapToResponse);
    }

    private StatusHistoryResponse mapToResponse(LessonQuestionStatusHistory history) {
        return StatusHistoryResponse.builder()
                .id(history.getId())
                .questionSubmissionId(history.getQuestionSubmission().getId())
                .previousStatus(history.getPreviousStatus())
                .newStatus(history.getNewStatus())
                .changedById(history.getChangedBy().getId())
                .changedByName(history.getChangedBy().getDisplayName())
                .createdAt(history.getCreatedAt())
                .changeReason(history.getChangeReason())
                .build();
    }
}



