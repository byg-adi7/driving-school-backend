package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;

import java.util.List;

public interface ResourceService {

    ResourceResponse create(CreateResourceRequest request, Long userId, String role);

    void delete(Long resourceId, Long userId, String role);

    List<ResourceResponse> getByLesson(Long lessonId, Long userId, String role);
}
