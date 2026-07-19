package com.drivingschool.backend.school.service;

import com.drivingschool.backend.school.dto.ReviewSchoolDeletionRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchoolDeletionRequestService {

    SchoolDeletionRequestResponse requestOwnSchoolDeletion(Long callerId);

    Page<SchoolDeletionRequestResponse> listPending(Pageable pageable, Long callerId);

    SchoolDeletionRequestResponse approve(Long requestId, Long callerId, ReviewSchoolDeletionRequest body);

    SchoolDeletionRequestResponse reject(Long requestId, Long callerId, ReviewSchoolDeletionRequest body);
}
