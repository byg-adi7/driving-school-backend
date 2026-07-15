package com.drivingschool.backend.lesson.question.repository;

import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonQuestionSubmissionRepository extends JpaRepository<LessonQuestionSubmission, Long> {

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs WHERE lqs.student.id = :studentId ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs WHERE lqs.instructor.id = :instructorId ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByInstructorId(@Param("instructorId") Long instructorId, Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs WHERE lqs.status = :status ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByStatus(@Param("status") QuestionStatus status, Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs WHERE lqs.instructor.id = :instructorId AND lqs.status = :status ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByInstructorAndStatus(
            @Param("instructorId") Long instructorId,
            @Param("status") QuestionStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(lqs) FROM LessonQuestionSubmission lqs WHERE lqs.instructor.id = :instructorId AND lqs.status IN (:statuses)")
    Long countPendingByInstructor(@Param("instructorId") Long instructorId, @Param("statuses") List<QuestionStatus> statuses);
}
