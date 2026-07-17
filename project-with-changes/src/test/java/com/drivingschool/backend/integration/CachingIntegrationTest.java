package com.drivingschool.backend.integration;

import com.drivingschool.backend.learning.dto.CourseResponse;
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
 * Verifies the Redis-backed @Cacheable/@CacheEvict wiring against a real Redis
 * container - unit tests with mocked repositories can't catch a wrong SpEL cache
 * key or a missing eviction, since the annotations are inert without a real
 * Spring AOP proxy and cache backend behind them.
 */
class CachingIntegrationTest extends AbstractIntegrationTest {

    /**
     * Regression test for a real bug found via a live docker-compose smoke test
     * (not caught by the other tests below, since every other assertion here
     * has an evicting mutation between calls - the second call is always a
     * fresh miss-then-repopulate, never a genuine cache hit): the first call
     * to any @Cacheable list endpoint always succeeds (cache miss, nothing to
     * deserialize yet), but a naive Jackson default-typing setup
     * (activateDefaultTyping with either As.PROPERTY or As.WRAPPER_ARRAY)
     * throws SerializationException on the second call, when Redis actually
     * has to deserialize a cached List. Roles has no mutation endpoint at all,
     * making back-to-back calls with nothing in between the cleanest way to
     * force a genuine hit.
     */
    @Test
    void rolesCache_survivesRepeatedCacheHit() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);

        MvcResult first = mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        // Cache hit - must not throw, and must return the same data as the miss.
        MvcResult second = mockMvc.perform(get("/api/v1/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void schoolsCache_reflectsNewSchoolAfterCreate() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);

        List<Object> before = getActiveSchools(adminToken);
        int countBefore = before.size();

        SchoolResponse created = createSchool(adminToken, "Cache Test Driving School " + System.nanoTime());

        List<Object> after = getActiveSchools(adminToken);
        assertThat(after).hasSize(countBefore + 1);

        MvcResult byIdResult = mockMvc.perform(get("/api/v1/schools/" + created.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(parse(byIdResult, SchoolResponse.class).getId()).isEqualTo(created.getId());
    }

    @Test
    void publishedCoursesCache_reflectsPublishThenUnpublishThenArchive() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "Cache Test Driving School Courses " + System.nanoTime());
        Person instructor = registerAndIdentify(adminToken, school.getId(), RoleName.INSTRUCTOR,
                "cache.instructor." + System.nanoTime() + "@example.com", "LIC-CACHE-1");

        List<Object> beforePublish = getPublishedCourses(adminToken);
        int countBeforePublish = beforePublish.size();

        CourseResponse course = createCourse(instructor, "Cache Test Course");

        // draft - not yet in the published list
        assertThat(getPublishedCourses(adminToken)).hasSize(countBeforePublish);

        mockMvc.perform(put("/api/v1/courses/" + course.getId() + "/publish")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk());

        // eviction on publish() must make this visible immediately, not after TTL
        assertThat(getPublishedCourses(adminToken)).hasSize(countBeforePublish + 1);

        mockMvc.perform(put("/api/v1/courses/" + course.getId() + "/unpublish")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk());

        // eviction on unpublish() must remove it immediately
        assertThat(getPublishedCourses(adminToken)).hasSize(countBeforePublish);
    }

    private List<Object> getActiveSchools(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/schools")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return parseDataList(result);
    }

    private List<Object> getPublishedCourses(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/courses")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return parseDataList(result);
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

    @SuppressWarnings("unchecked")
    private List<Object> parseDataList(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (List<Object>) body.get("data");
    }
}
