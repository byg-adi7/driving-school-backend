package com.drivingschool.backend.instructor.service;

import com.drivingschool.backend.instructor.dto.InstructorProfileResponse;
import com.drivingschool.backend.instructor.dto.UpdateInstructorActiveStatusRequest;
import com.drivingschool.backend.instructor.dto.UpdateInstructorProfileRequest;

import java.util.List;

public interface InstructorProfileService {

    InstructorProfileResponse getMyProfile();

    InstructorProfileResponse updateMyProfile(UpdateInstructorProfileRequest request);

    List<InstructorProfileResponse> getBySchool(Long schoolId);

    InstructorProfileResponse updateActiveStatus(Long id, UpdateInstructorActiveStatusRequest request);
}
