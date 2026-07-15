package com.drivingschool.backend.quiz.repository;

import com.drivingschool.backend.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCourseIdAndPublishedTrue(Long courseId);
}
