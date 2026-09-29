package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.enums.CourseStatus;
import com.drivingschool.backend.learning.mapper.LearningMapper;
import com.drivingschool.backend.learning.repository.CourseRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The cached half of CourseService.getPublished(): the published-course list for one
 * school, or for every school when schoolId is null (the bootstrap admin).
 *
 * Its own bean because Spring's cache proxy only intercepts calls coming from outside
 * the bean. CourseServiceImpl resolves the caller-dependent part (which school) first
 * and passes it in, so every cache entry is itself the same for every caller - one
 * entry per school, never one school's list served to another.
 */
@Component
public class PublishedCourseCatalog {

    private final CourseRepository courseRepository;
    private final LearningMapper mapper;

    public PublishedCourseCatalog(CourseRepository courseRepository, LearningMapper mapper) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    @Cacheable(value = "courses", key = "'published:' + #schoolId")
    @Transactional(readOnly = true)
    public List<CourseResponse> forSchool(Long schoolId) {
        List<Course> courses = schoolId == null
                ? courseRepository.findByStatus(CourseStatus.PUBLISHED)
                : courseRepository.findByStatusAndInstructor_School_Id(CourseStatus.PUBLISHED, schoolId);
        return courses.stream()
                .map(mapper::toResponse)
                .toList();
    }
}
