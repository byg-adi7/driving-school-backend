package com.drivingschool.backend.lesson.note.repository;

import com.drivingschool.backend.lesson.note.entity.LessonNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonNoteRepository extends JpaRepository<LessonNote, Long> {

    // LessonNoteService#mapToResponse dereferences instructor, student, and
    // updatedBy (plus their name-bearing associations) for every row -
    // fetch-joining all of them here avoids a per-row N+1 regardless of which
    // listing method the caller uses.
    @Query("SELECT ln FROM LessonNote ln JOIN FETCH ln.instructor i JOIN FETCH i.user "
            + "JOIN FETCH ln.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH ln.updatedBy ub LEFT JOIN FETCH ub.studentProfile LEFT JOIN FETCH ub.instructorProfile "
            + "WHERE ln.student.id = :studentId ORDER BY ln.createdAt DESC")
    Page<LessonNote> findByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    @Query("SELECT ln FROM LessonNote ln JOIN FETCH ln.instructor i JOIN FETCH i.user "
            + "JOIN FETCH ln.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH ln.updatedBy ub LEFT JOIN FETCH ub.studentProfile LEFT JOIN FETCH ub.instructorProfile "
            + "WHERE ln.instructor.id = :instructorId ORDER BY ln.createdAt DESC")
    Page<LessonNote> findByInstructorId(@Param("instructorId") Long instructorId, Pageable pageable);

    @Query("SELECT ln FROM LessonNote ln JOIN FETCH ln.instructor i JOIN FETCH i.user "
            + "JOIN FETCH ln.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH ln.updatedBy ub LEFT JOIN FETCH ub.studentProfile LEFT JOIN FETCH ub.instructorProfile "
            + "WHERE ln.student.id = :studentId AND ln.instructor.id = :instructorId ORDER BY ln.createdAt DESC")
    Page<LessonNote> findByStudentAndInstructor(
            @Param("studentId") Long studentId,
            @Param("instructorId") Long instructorId,
            Pageable pageable
    );

    @Query("SELECT ln FROM LessonNote ln JOIN FETCH ln.instructor i JOIN FETCH i.user "
            + "JOIN FETCH ln.student st JOIN FETCH st.user "
            + "LEFT JOIN FETCH ln.updatedBy ub LEFT JOIN FETCH ub.studentProfile LEFT JOIN FETCH ub.instructorProfile "
            + "ORDER BY ln.createdAt DESC")
    Page<LessonNote> findAllNotes(Pageable pageable);

    boolean existsByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);
}
