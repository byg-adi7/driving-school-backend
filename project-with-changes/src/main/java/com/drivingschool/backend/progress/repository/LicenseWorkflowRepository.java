package com.drivingschool.backend.progress.repository;

import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LicenseWorkflowRepository extends JpaRepository<LicenseWorkflow, Long> {

    Optional<LicenseWorkflow> findByStudentId(Long studentId);

    boolean existsByStudentId(Long studentId);
}
