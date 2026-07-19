package com.drivingschool.backend.school.repository;

import com.drivingschool.backend.school.entity.SchoolDeletionRequest;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolDeletionRequestRepository extends JpaRepository<SchoolDeletionRequest, Long> {

    boolean existsBySchoolIdAndStatus(Long schoolId, SchoolDeletionRequestStatus status);

    Optional<SchoolDeletionRequest> findBySchoolIdAndStatus(Long schoolId, SchoolDeletionRequestStatus status);

    Page<SchoolDeletionRequest> findByStatus(SchoolDeletionRequestStatus status, Pageable pageable);
}
