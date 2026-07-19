package com.drivingschool.backend.progress.repository;

import com.drivingschool.backend.progress.entity.DrivingAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrivingAssessmentRepository extends JpaRepository<DrivingAssessment, Long> {

    List<DrivingAssessment> findByStudentIdOrderByAssessmentDateDesc(Long studentId);

    List<DrivingAssessment> findByInstructorIdOrderByAssessmentDateDesc(Long instructorId);

    boolean existsByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);
}
