package com.drivingschool.backend.quiz.repository;

import com.drivingschool.backend.quiz.entity.QuizSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    long countByQuizIdAndStudentId(Long quizId, Long studentId);

    Optional<QuizSubmission> findTopByQuizIdAndStudentIdOrderByAttemptNumberDesc(Long quizId, Long studentId);

    /** One student's attempts at one quiz, in a single query: how many, best score, ever passed. */
    @Query("SELECT COUNT(s) AS attempts, MAX(s.score) AS bestScore, "
            + "MAX(CASE WHEN s.passed = true THEN 1 ELSE 0 END) AS passed "
            + "FROM QuizSubmission s WHERE s.quiz.id = :quizId AND s.student.id = :studentId")
    AttemptSummary summarizeAttempts(@Param("quizId") Long quizId, @Param("studentId") Long studentId);

    interface AttemptSummary {
        Long getAttempts();

        Integer getBestScore();

        Integer getPassed();
    }
}
