package com.drivingschool.backend.learning.repository;

import com.drivingschool.backend.learning.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByLessonIdOrderByIdAsc(Long lessonId);
}
