package com.drivingschool.backend.lesson.question.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.lesson.question.dto.QuestionResponse;
import com.drivingschool.backend.lesson.question.dto.RespondToQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.SubmitQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.UpdateQuestionStatusRequest;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionStatusHistoryRepository;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionSubmissionRepository;
import com.drivingschool.backend.lesson.question.validator.QuestionValidator;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LessonQuestionSubmissionService {

    private final LessonQuestionSubmissionRepository questionRepository;
    private final LessonQuestionStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final LessonQuestionStatusHistoryService statusHistoryService;
    private final QuestionValidator validator;

    @Transactional
    public QuestionResponse submitQuestion(SubmitQuestionRequest request, Long studentId) {
        validator.validateSubmitRequest(request);

        var studentProfile = studentProfileRepository.findByUserId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user ID: " + studentId));

        var instructorProfile = (request.getAssignedInstructorId() != null)
                ? instructorProfileRepository.findByUserId(request.getAssignedInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + request.getAssignedInstructorId()))
                : null;

        LessonQuestionSubmission question = LessonQuestionSubmission.builder()
                .student(studentProfile)
                .instructor(instructorProfile)
                .subject(request.getSubject())
                .questionBody(request.getQuestionBody())
                .status(QuestionStatus.PENDING)
                .build();

        LessonQuestionSubmission savedQuestion = questionRepository.save(question);

        // record status change with the student user as actor
        statusHistoryService.recordStatusChange(
                savedQuestion,
                null,
                QuestionStatus.PENDING,
                studentProfile.getUser(),
                "Question submitted"
        );

        return mapToResponse(savedQuestion);
    }

    @Transactional
    public QuestionResponse respondToQuestion(Long questionId, RespondToQuestionRequest request, Long instructorId) {
        validator.validateRespondRequest(request);

        LessonQuestionSubmission question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));

        validator.validateInstructorAccess(question, instructorId);

        var instructorProfile = instructorProfileRepository.findByUserId(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + instructorId));

        question.setResponse(request.getResponse());
        question.setStatus(QuestionStatus.ANSWERED);
        question.setRespondedBy(instructorProfile.getUser());
        question.setRespondedAt(LocalDateTime.now());

        LessonQuestionSubmission updatedQuestion = questionRepository.save(question);

        statusHistoryService.recordStatusChange(
                updatedQuestion,
                QuestionStatus.PENDING,
                QuestionStatus.ANSWERED,
                instructorProfile.getUser(),
                "Response provided"
        );

        return mapToResponse(updatedQuestion);
    }

    @Transactional
    public QuestionResponse updateQuestionStatus(Long questionId, UpdateQuestionStatusRequest request, Long userId, String role) {
        LessonQuestionSubmission question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));

        validator.validateStatusUpdate(question, request.getNewStatus(), userId, role);

        QuestionStatus previousStatus = question.getStatus();
        question.setStatus(request.getNewStatus());

        LessonQuestionSubmission updatedQuestion = questionRepository.save(question);

        // use the user entity as the actor for history
        User changedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        statusHistoryService.recordStatusChange(
                updatedQuestion,
                previousStatus,
                request.getNewStatus(),
                changedBy,
                request.getChangeReason()
        );

        return mapToResponse(updatedQuestion);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long questionId, Long userId, String role) {
        LessonQuestionSubmission question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + questionId));

        validator.validateReadAccess(question, userId, role);
        return mapToResponse(question);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getStudentQuestions(Long studentId, Pageable pageable) {
        Long profileId = studentProfileRepository.findByUserId(studentId)
                .map(p -> p.getId())
                .orElse(studentId);

        Page<LessonQuestionSubmission> questions = questionRepository.findByStudentId(profileId, pageable);
        return questions.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getInstructorQuestions(Long instructorId, Pageable pageable) {
        Long profileId = instructorProfileRepository.findByUserId(instructorId)
                .map(p -> p.getId())
                .orElse(instructorId);

        Page<LessonQuestionSubmission> questions = questionRepository.findByInstructorId(profileId, pageable);
        return questions.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getQuestionsByStatus(QuestionStatus status, Pageable pageable) {
        Page<LessonQuestionSubmission> questions = questionRepository.findByStatus(status, pageable);
        return questions.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> getInstructorPendingQuestions(Long instructorId, Pageable pageable) {
        Long profileId = instructorProfileRepository.findByUserId(instructorId)
                .map(p -> p.getId())
                .orElse(instructorId);

        Page<LessonQuestionSubmission> questions = questionRepository.findByInstructorAndStatus(
                profileId,
                QuestionStatus.PENDING,
                pageable
        );
        return questions.map(this::mapToResponse);
    }

    private QuestionResponse mapToResponse(LessonQuestionSubmission question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .studentId(question.getStudent().getUser().getId())
                .studentName(question.getStudent().getUser().getDisplayName())
                .instructorId(question.getInstructor() != null ? question.getInstructor().getUser().getId() : null)
                .instructorName(question.getInstructor() != null ? question.getInstructor().getUser().getDisplayName() : null)
                .subject(question.getSubject())
                .questionBody(question.getQuestionBody())
                .response(question.getResponse())
                .status(question.getStatus())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .respondedById(question.getRespondedBy() != null ? question.getRespondedBy().getId() : null)
                .respondedByName(question.getRespondedBy() != null ? question.getRespondedBy().getDisplayName() : null)
                .respondedAt(question.getRespondedAt())
                .build();
    }
}

