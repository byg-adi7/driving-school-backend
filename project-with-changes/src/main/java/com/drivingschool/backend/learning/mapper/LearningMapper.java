package com.drivingschool.backend.learning.mapper;

import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.dto.VideoLessonResponse;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.entity.Resource;
import com.drivingschool.backend.learning.entity.VideoLesson;
import org.springframework.stereotype.Component;

@Component
public class LearningMapper {

    public CourseResponse toResponse(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .instructorId(course.getInstructor().getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .status(course.getStatus())
                .publishedAt(course.getPublishedAt())
                .build();
    }

    public VideoLessonResponse toResponse(VideoLesson lesson) {
        return VideoLessonResponse.builder()
                .id(lesson.getId())
                .courseId(lesson.getCourse().getId())
                .title(lesson.getTitle())
                .description(lesson.getDescription())
                .videoUrl(lesson.getVideoUrl())
                .lessonOrder(lesson.getLessonOrder())
                .durationSeconds(lesson.getDurationSeconds())
                .published(lesson.isPublished())
                .build();
    }

    public ResourceResponse toResponse(Resource resource) {
        return ResourceResponse.builder()
                .id(resource.getId())
                .lessonId(resource.getLesson().getId())
                .title(resource.getTitle())
                .fileUrl(resource.getFileUrl())
                .type(resource.getType())
                .build();
    }
}
