package com.drivingschool.backend.learning.repository;

import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.enums.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByInstructor_IdAndStatus(Long instructorId, CourseStatus status);

    List<Course> findByStatus(CourseStatus status);

    List<Course> findByInstructor_Id(Long instructorId);
}
