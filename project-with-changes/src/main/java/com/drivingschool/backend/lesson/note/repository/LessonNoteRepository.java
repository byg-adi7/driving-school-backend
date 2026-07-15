package com.drivingschool.backend.lesson.note.repository;

import com.drivingschool.backend.lesson.note.entity.LessonNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonNoteRepository extends JpaRepository<LessonNote, Long> {

    @Query("SELECT ln FROM LessonNote ln WHERE ln.liveSessionId = :liveSessionId")
    Optional<LessonNote> findByLiveSessionId(@Param("liveSessionId") Long liveSessionId);

    @Query("SELECT ln FROM LessonNote ln WHERE ln.student.id = :studentId ORDER BY ln.createdAt DESC")
    Page<LessonNote> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT ln FROM LessonNote ln WHERE ln.instructor.id = :instructorId ORDER BY ln.createdAt DESC")
    Page<LessonNote> findByInstructorId(@Param("instructorId") Long instructorId, Pageable pageable);

    @Query("SELECT ln FROM LessonNote ln WHERE ln.student.id = :studentId AND ln.instructor.id = :instructorId ORDER BY ln.createdAt DESC")
    Page<LessonNote> findByStudentAndInstructor(
            @Param("studentId") Long studentId,
            @Param("instructorId") Long instructorId,
            Pageable pageable
    );

    @Query("SELECT ln FROM LessonNote ln ORDER BY ln.createdAt DESC")
    Page<LessonNote> findAllNotes(Pageable pageable);

    boolean existsByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);
}
