package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.learning.dto.CreateCourseRequest;
import com.drivingschool.backend.learning.dto.UpdateCourseRequest;

import java.util.List;

public interface CourseService {

    CourseResponse create(CreateCourseRequest request, Long userId, String role);

    CourseResponse update(Long courseId, UpdateCourseRequest request, Long userId, String role);

    CourseResponse publish(Long courseId, Long userId, String role);

    CourseResponse unpublish(Long courseId, Long userId, String role);

    CourseResponse archive(Long courseId, Long userId, String role);

    CourseResponse getById(Long courseId, Long userId, String role);

    List<CourseResponse> getPublished();

    List<CourseResponse> getMine(Long userId);
}
