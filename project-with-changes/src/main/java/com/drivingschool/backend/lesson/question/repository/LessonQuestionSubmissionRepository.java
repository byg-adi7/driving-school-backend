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

    // Every listing method feeds LessonQuestionSubmissionService#mapToResponse, which
    // dereferences student, instructor, and respondedBy (plus their name-bearing
    // associations) for every row - fetch-joining all of them here turns what would
    // otherwise be a per-row N+1 into a single query, regardless of which listing
    // method the caller uses.
    @Query("SELECT lqs FROM LessonQuestionSubmission lqs "
            + "JOIN FETCH lqs.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH lqs.instructor i LEFT JOIN FETCH i.user "
            + "LEFT JOIN FETCH lqs.respondedBy rb LEFT JOIN FETCH rb.studentProfile LEFT JOIN FETCH rb.instructorProfile "
            + "WHERE lqs.student.id = :studentId ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs "
            + "JOIN FETCH lqs.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH lqs.instructor i LEFT JOIN FETCH i.user "
            + "LEFT JOIN FETCH lqs.respondedBy rb LEFT JOIN FETCH rb.studentProfile LEFT JOIN FETCH rb.instructorProfile "
            + "WHERE lqs.instructor.id = :instructorId ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByInstructorId(@Param("instructorId") Long instructorId, Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs "
            + "JOIN FETCH lqs.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH lqs.instructor i LEFT JOIN FETCH i.user "
            + "LEFT JOIN FETCH lqs.respondedBy rb LEFT JOIN FETCH rb.studentProfile LEFT JOIN FETCH rb.instructorProfile "
            + "WHERE lqs.status = :status ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByStatus(@Param("status") QuestionStatus status, Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs "
            + "JOIN FETCH lqs.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH lqs.instructor i LEFT JOIN FETCH i.user "
            + "LEFT JOIN FETCH lqs.respondedBy rb LEFT JOIN FETCH rb.studentProfile LEFT JOIN FETCH rb.instructorProfile "
            + "WHERE lqs.status = :status AND st.school.id = :schoolId ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByStatusAndSchoolId(@Param("status") QuestionStatus status,
                                                          @Param("schoolId") Long schoolId,
                                                          Pageable pageable);

    @Query("SELECT lqs FROM LessonQuestionSubmission lqs "
            + "JOIN FETCH lqs.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH lqs.instructor i LEFT JOIN FETCH i.user "
            + "LEFT JOIN FETCH lqs.respondedBy rb LEFT JOIN FETCH rb.studentProfile LEFT JOIN FETCH rb.instructorProfile "
            + "WHERE lqs.instructor.id = :instructorId AND lqs.status = :status ORDER BY lqs.createdAt DESC")
    Page<LessonQuestionSubmission> findByInstructorAndStatus(
            @Param("instructorId") Long instructorId,
            @Param("status") QuestionStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(lqs) FROM LessonQuestionSubmission lqs WHERE lqs.instructor.id = :instructorId AND lqs.status IN (:statuses)")
    Long countPendingByInstructor(@Param("instructorId") Long instructorId, @Param("statuses") List<QuestionStatus> statuses);
}
