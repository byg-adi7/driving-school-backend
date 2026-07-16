package com.drivingschool.backend.instructor.repository;

import com.drivingschool.backend.instructor.entity.InstructorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {

    Optional<InstructorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("SELECT ip FROM InstructorProfile ip JOIN FETCH ip.user u WHERE ip.school.id = :schoolId AND u.deletedAt IS NULL")
    List<InstructorProfile> findBySchoolIdExcludingDeletedUsers(@Param("schoolId") Long schoolId);
}
