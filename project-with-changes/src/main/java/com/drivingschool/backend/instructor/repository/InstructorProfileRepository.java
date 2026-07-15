package com.drivingschool.backend.instructor.repository;

import com.drivingschool.backend.instructor.entity.InstructorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {

    Optional<InstructorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
