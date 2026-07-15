package com.drivingschool.backend.learning.repository;

import com.drivingschool.backend.learning.entity.VideoLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoLessonRepository extends JpaRepository<VideoLesson, Long> {

    List<VideoLesson> findByCourseIdOrderByLessonOrderAsc(Long courseId);
}
