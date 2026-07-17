package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.learning.dto.CreateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.UpdateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.VideoLessonResponse;

import java.util.List;

public interface VideoLessonService {

    VideoLessonResponse create(CreateVideoLessonRequest request, Long userId, String role);

    VideoLessonResponse update(Long lessonId, UpdateVideoLessonRequest request, Long userId, String role);

    VideoLessonResponse publish(Long lessonId, Long userId, String role);

    VideoLessonResponse unpublish(Long lessonId, Long userId, String role);

    VideoLessonResponse getById(Long lessonId, Long userId, String role);

    List<VideoLessonResponse> getByCourse(Long courseId, Long userId, String role);
}
