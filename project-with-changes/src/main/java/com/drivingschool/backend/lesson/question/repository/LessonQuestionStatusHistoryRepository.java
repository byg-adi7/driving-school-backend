package com.drivingschool.backend.lesson.question.repository;

import com.drivingschool.backend.lesson.question.entity.LessonQuestionStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonQuestionStatusHistoryRepository extends JpaRepository<LessonQuestionStatusHistory, Long> {

    @Query("SELECT lqsh FROM LessonQuestionStatusHistory lqsh WHERE lqsh.questionSubmission.id = :questionId ORDER BY lqsh.createdAt DESC")
    List<LessonQuestionStatusHistory> findByQuestionSubmissionId(@Param("questionId") Long questionId);

    @Query("SELECT lqsh FROM LessonQuestionStatusHistory lqsh WHERE lqsh.questionSubmission.id = :questionId ORDER BY lqsh.createdAt DESC")
    Page<LessonQuestionStatusHistory> findByQuestionSubmissionIdPaginated(
            @Param("questionId") Long questionId,
            Pageable pageable
    );

    @Query("SELECT lqsh FROM LessonQuestionStatusHistory lqsh WHERE lqsh.changedBy.id = :userId ORDER BY lqsh.createdAt DESC")
    Page<LessonQuestionStatusHistory> findByChangedById(@Param("userId") Long userId, Pageable pageable);
}
