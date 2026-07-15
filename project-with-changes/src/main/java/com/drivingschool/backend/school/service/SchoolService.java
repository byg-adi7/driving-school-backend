package com.drivingschool.backend.school.service;

import com.drivingschool.backend.school.dto.CreateSchoolRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;

import java.util.List;

public interface SchoolService {

    SchoolResponse create(CreateSchoolRequest request);

    SchoolResponse getById(Long id);

    List<SchoolResponse> getAllActive();
}
