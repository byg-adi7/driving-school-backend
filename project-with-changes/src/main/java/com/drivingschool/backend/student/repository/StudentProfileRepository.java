package com.drivingschool.backend.student.repository;

import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("SELECT sp FROM StudentProfile sp WHERE sp.school.id = :schoolId AND sp.user.deletedAt IS NULL")
    List<StudentProfile> findBySchoolIdExcludingDeletedUsers(@Param("schoolId") Long schoolId);
}
