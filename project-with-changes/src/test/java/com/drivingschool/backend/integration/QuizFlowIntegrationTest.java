package com.drivingschool.backend.integration;

import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.quiz.dto.QuizQuestionResponse;
import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.dto.QuizSubmissionResponse;
import com.drivingschool.backend.quiz.enums.QuestionType;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full quiz workflow end to end through real HTTP calls against
 * a real Postgres + Redis stack: course/quiz/question authoring, draft-vs-published
 * visibility, scoring, and max-attempts enforcement - including the identity-spoofing
 * guard on submission (a student can never submit as anyone but themselves).
 */
class QuizFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void instructorAuthorsQuiz_studentSubmitsAndExhaustsAttempts() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "Quiz Flow Driving School");

        Person instructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "instructor.quiz@example.com", "LIC-QUIZ-1");
        Person otherInstructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "other.instructor.quiz@example.com", "LIC-QUIZ-2");
        Person student = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "student.quiz@example.com", null);
        Person otherStudent = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "other.student.quiz@example.com", null);

        CourseResponse course = createCourse(instructor, "Road Signs 101");

        // student can't create a quiz at all - role-gated
        mockMvc.perform(post("/api/v1/quizzes")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", course.getId(), "title", "Illicit Quiz",
                                "passingScore", 70, "maxAttempts", 1))))
                .andExpect(status().isForbidden());

        QuizResponse quiz = createQuiz(instructor, course.getId(), "Road Signs Final", 70, 2);

        // an unrelated instructor can't add questions to someone else's quiz
        mockMvc.perform(post("/api/v1/quizzes/" + quiz.getId() + "/questions")
                        .header("Authorization", bearer(otherInstructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("What does a red light mean?", "Stop", 50, 1)))
                .andExpect(status().isBadRequest());

        Long q1Id = addQuestion(instructor, quiz.getId(), "What does a red light mean?", "Stop", 50, 1);
        Long q2Id = addQuestion(instructor, quiz.getId(), "What does a green light mean?", "Go", 50, 2);

        // unpublished: not visible in the course's published list, and denied to an unrelated student
        MvcResult prePublishListResult = mockMvc.perform(get("/api/v1/quizzes/course/" + course.getId())
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parseList(prePublishListResult)).isEmpty();

        mockMvc.perform(get("/api/v1/quizzes/" + quiz.getId())
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isBadRequest());

        // publish
        MvcResult publishResult = mockMvc.perform(put("/api/v1/quizzes/" + quiz.getId() + "/publish")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parse(publishResult, QuizResponse.class).isPublished()).isTrue();

        // now visible to any student
        MvcResult postPublishListResult = mockMvc.perform(get("/api/v1/quizzes/course/" + course.getId())
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parseList(postPublishListResult)).hasSize(1);

        // first attempt: all correct -> full score, passed
        MvcResult firstSubmission = mockMvc.perform(post("/api/v1/quizzes/" + quiz.getId() + "/submit")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", student.profileId(),
                                "answers", Map.of(q1Id.toString(), "Stop", q2Id.toString(), "Go")))))
                .andExpect(status().isOk())
                .andReturn();
        QuizSubmissionResponse first = parse(firstSubmission, QuizSubmissionResponse.class);
        assertThat(first.getScore()).isEqualTo(100);
        assertThat(first.isPassed()).isTrue();
        assertThat(first.getAttemptNumber()).isEqualTo(1);
        assertThat(first.getStudentId()).isEqualTo(student.profileId());

        // a student can never submit as someone else, even if they claim another student's ID in the body
        MvcResult spoofedSubmission = mockMvc.perform(post("/api/v1/quizzes/" + quiz.getId() + "/submit")
                        .header("Authorization", bearer(otherStudent.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", student.profileId(),
                                "answers", Map.of(q1Id.toString(), "Stop", q2Id.toString(), "Go")))))
                .andExpect(status().isOk())
                .andReturn();
        QuizSubmissionResponse spoofed = parse(spoofedSubmission, QuizSubmissionResponse.class);
        assertThat(spoofed.getStudentId()).isEqualTo(otherStudent.profileId());

        // second attempt: all wrong -> zero score, not passed
        MvcResult secondSubmission = mockMvc.perform(post("/api/v1/quizzes/" + quiz.getId() + "/submit")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", student.profileId(),
                                "answers", Map.of(q1Id.toString(), "Go", q2Id.toString(), "Stop")))))
                .andExpect(status().isOk())
                .andReturn();
        QuizSubmissionResponse second = parse(secondSubmission, QuizSubmissionResponse.class);
        assertThat(second.getScore()).isEqualTo(0);
        assertThat(second.isPassed()).isFalse();
        assertThat(second.getAttemptNumber()).isEqualTo(2);

        // third attempt exceeds maxAttempts(2) -> rejected
        mockMvc.perform(post("/api/v1/quizzes/" + quiz.getId() + "/submit")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", student.profileId(),
                                "answers", Map.of(q1Id.toString(), "Stop", q2Id.toString(), "Go")))))
                .andExpect(status().isBadRequest());
    }

    private CourseResponse createCourse(Person instructor, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", title))))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result, CourseResponse.class);
    }

    private QuizResponse createQuiz(Person instructor, Long courseId, String title, int passingScore, int maxAttempts) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/quizzes")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", courseId, "title", title,
                                "passingScore", passingScore, "maxAttempts", maxAttempts))))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result, QuizResponse.class);
    }

    private Long addQuestion(Person instructor, Long quizId, String questionText, String correctAnswer,
                              int points, int order) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/questions")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson(questionText, correctAnswer, points, order)))
                .andExpect(status().isOk())
                .andReturn();
        QuizResponse response = parse(result, QuizResponse.class);
        return response.getQuestions().stream()
                .filter(q -> q.getQuestionOrder().equals(order))
                .map(QuizQuestionResponse::getId)
                .findFirst()
                .orElseThrow();
    }

    private String questionJson(String questionText, String correctAnswer, int points, int order) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "questionText", questionText,
                "questionType", QuestionType.SHORT_ANSWER.name(),
                "correctAnswer", correctAnswer,
                "points", points,
                "questionOrder", order));
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseList(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (List<Object>) body.get("data");
    }
}
