package com.drivingschool.backend.student.service;

import com.drivingschool.backend.student.dto.StudentProfileResponse;
import com.drivingschool.backend.student.dto.UpdateStudentProfileRequest;
import com.drivingschool.backend.student.dto.UpdateStudentStatusRequest;

import java.util.List;

public interface StudentProfileService {

    StudentProfileResponse getMyProfile();

    StudentProfileResponse updateMyProfile(UpdateStudentProfileRequest request);

    List<StudentProfileResponse> getBySchool(Long schoolId);

    StudentProfileResponse updateStatus(Long id, UpdateStudentStatusRequest request);
}
