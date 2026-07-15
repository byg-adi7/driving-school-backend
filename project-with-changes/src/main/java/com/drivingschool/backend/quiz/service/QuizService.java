package com.drivingschool.backend.quiz.service;

import com.drivingschool.backend.quiz.dto.CreateQuizQuestionRequest;
import com.drivingschool.backend.quiz.dto.CreateQuizRequest;
import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.dto.QuizSubmissionResponse;
import com.drivingschool.backend.quiz.dto.SubmitQuizRequest;

import java.util.List;

public interface QuizService {

    QuizResponse create(CreateQuizRequest request, Long userId, String role);

    QuizResponse addQuestion(Long quizId, CreateQuizQuestionRequest request, Long userId, String role);

    QuizResponse publish(Long quizId, Long userId, String role);

    QuizResponse getById(Long quizId, boolean forStudent, Long userId, String role);

    List<QuizResponse> getPublishedByCourse(Long courseId);

    QuizSubmissionResponse submit(Long quizId, SubmitQuizRequest request, Long userId, String role);
}
