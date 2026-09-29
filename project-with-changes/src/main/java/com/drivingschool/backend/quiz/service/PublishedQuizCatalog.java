package com.drivingschool.backend.quiz.service;

import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.entity.QuizQuestion;
import com.drivingschool.backend.quiz.mapper.QuizMapper;
import com.drivingschool.backend.quiz.repository.QuizQuestionRepository;
import com.drivingschool.backend.quiz.repository.QuizRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The cached half of QuizService.getPublishedByCourse(): a course's published quizzes,
 * answers always stripped - identical for every caller who may see the course at all.
 *
 * Its own bean because Spring's cache proxy only intercepts calls coming from outside
 * the bean: QuizServiceImpl checks the caller may read the course (its school) first,
 * on every request, and only then asks this bean - so a cache hit can never skip
 * that check. Evicted by QuizServiceImpl's own @CacheEvict(key = courseId) methods.
 */
@Component
public class PublishedQuizCatalog {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizMapper quizMapper;

    public PublishedQuizCatalog(QuizRepository quizRepository,
                                QuizQuestionRepository quizQuestionRepository,
                                QuizMapper quizMapper) {
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizMapper = quizMapper;
    }

    @Cacheable(value = "quizzes-by-course", key = "#courseId")
    @Transactional(readOnly = true)
    public List<QuizResponse> forCourse(Long courseId) {
        return quizRepository.findByCourseIdAndPublishedTrue(courseId).stream()
                .map(quiz -> {
                    List<QuizQuestion> questions =
                            quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quiz.getId());
                    return quizMapper.toResponse(quiz, questions, false);
                })
                .toList();
    }
}
