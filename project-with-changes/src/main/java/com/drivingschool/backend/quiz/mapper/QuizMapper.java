package com.drivingschool.backend.quiz.mapper;

import com.drivingschool.backend.quiz.dto.QuizQuestionResponse;
import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.dto.QuizSubmissionResponse;
import com.drivingschool.backend.quiz.entity.Quiz;
import com.drivingschool.backend.quiz.entity.QuizQuestion;
import com.drivingschool.backend.quiz.entity.QuizSubmission;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuizMapper {

    public QuizResponse toResponse(Quiz quiz, List<QuizQuestion> questions, boolean includeAnswers) {
        List<QuizQuestionResponse> questionResponses = questions.stream()
                .map(q -> toQuestionResponse(q, includeAnswers))
                .toList();

        return QuizResponse.builder()
                .id(quiz.getId())
                .courseId(quiz.getCourse().getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .passingScore(quiz.getPassingScore())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .maxAttempts(quiz.getMaxAttempts())
                .published(quiz.isPublished())
                .questions(questionResponses)
                .build();
    }

    public QuizQuestionResponse toQuestionResponse(QuizQuestion question, boolean includeAnswers) {
        return QuizQuestionResponse.builder()
                .id(question.getId())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .options(question.getOptions())
                .points(question.getPoints())
                .questionOrder(question.getQuestionOrder())
                .build();
    }

    public QuizSubmissionResponse toSubmissionResponse(QuizSubmission submission) {
        return QuizSubmissionResponse.builder()
                .id(submission.getId())
                .quizId(submission.getQuiz().getId())
                .studentId(submission.getStudent().getId())
                .attemptNumber(submission.getAttemptNumber())
                .score(submission.getScore())
                .passed(submission.isPassed())
                .status(submission.getStatus())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
}
