package com.drivingschool.backend.quiz.repository;

import com.drivingschool.backend.quiz.entity.QuizSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    long countByQuizIdAndStudentId(Long quizId, Long studentId);

    Optional<QuizSubmission> findTopByQuizIdAndStudentIdOrderByAttemptNumberDesc(Long quizId, Long studentId);
}
