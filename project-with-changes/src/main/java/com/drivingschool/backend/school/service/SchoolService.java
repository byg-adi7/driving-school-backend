package com.drivingschool.backend.school.service;

import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;

import java.util.List;

public interface SchoolService {

    SchoolWithAdminResponse createWithAdmin(CreateSchoolWithAdminRequest request);

    SchoolResponse getById(Long id, Long callerId, String callerRole);

    List<SchoolResponse> getAllActive(Long callerId, String callerRole);

    void deleteDirectly(Long schoolId);
}
