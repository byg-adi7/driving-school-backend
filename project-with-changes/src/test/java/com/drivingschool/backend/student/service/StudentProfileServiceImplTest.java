package com.drivingschool.backend.student.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.dto.StudentProfileResponse;
import com.drivingschool.backend.student.dto.UpdateStudentProfileRequest;
import com.drivingschool.backend.student.dto.UpdateStudentStatusRequest;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.enums.StudentStatus;
import com.drivingschool.backend.student.mapper.StudentProfileMapper;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceImplTest {

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private CurrentUserService currentUserService;
    private final StudentProfileMapper studentProfileMapper = new StudentProfileMapper();

    private StudentProfileServiceImpl studentProfileService;

    @BeforeEach
    void setUp() {
        studentProfileService = new StudentProfileServiceImpl(
                studentProfileRepository, studentProfileMapper, currentUserService);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("student@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private School schoolWithId(Long id) {
        School school = School.builder().name("Test School").address("1 Main St").active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private StudentProfile profileWithId(Long id, User user, School school) {
        StudentProfile profile = StudentProfile.builder()
                .firstName("Jane").lastName("Doe").phone("123")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .enrollmentDate(LocalDate.of(2024, 1, 1))
                .status(StudentStatus.ACTIVE)
                .school(school).user(user).build();
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    @Test
    void getMyProfile_whenProfileExists_returnsMappedResponse() {
        User user = userWithId(1L);
        School school = schoolWithId(1L);
        StudentProfile profile = profileWithId(10L, user, school);
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        StudentProfileResponse response = studentProfileService.getMyProfile();

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getEmail()).isEqualTo("student@example.com");
    }

    @Test
    void getMyProfile_whenProfileMissing_throwsResourceNotFoundException() {
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentProfileService.getMyProfile())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMyProfile_withValidRequest_updatesEditableFields() {
        User user = userWithId(1L);
        School school = schoolWithId(1L);
        StudentProfile profile = profileWithId(10L, user, school);
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenReturn(profile);
        UpdateStudentProfileRequest request = UpdateStudentProfileRequest.builder()
                .firstName("Janet").lastName("Smith").phone("999")
                .dateOfBirth(LocalDate.of(1999, 5, 5)).profileImageUrl("http://img").build();

        StudentProfileResponse response = studentProfileService.updateMyProfile(request);

        assertThat(response.getFirstName()).isEqualTo("Janet");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getStatus()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    void getBySchool_returnsAllStudentsForSchool() {
        User user1 = userWithId(1L);
        User user2 = userWithId(2L);
        School school = schoolWithId(1L);
        StudentProfile p1 = profileWithId(10L, user1, school);
        StudentProfile p2 = profileWithId(11L, user2, school);
        when(studentProfileRepository.findBySchoolIdExcludingDeletedUsers(1L)).thenReturn(List.of(p1, p2));

        List<StudentProfileResponse> responses = studentProfileService.getBySchool(1L);

        assertThat(responses).hasSize(2);
    }

    @Test
    void updateStatus_whenNotFound_throwsResourceNotFoundException() {
        when(studentProfileRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder().status(StudentStatus.SUSPENDED).build();

        assertThatThrownBy(() -> studentProfileService.updateStatus(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_withValidRequest_updatesStatus() {
        User user = userWithId(1L);
        School school = schoolWithId(1L);
        StudentProfile profile = profileWithId(10L, user, school);
        when(studentProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(studentProfileRepository.save(any(StudentProfile.class))).thenReturn(profile);
        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder().status(StudentStatus.GRADUATED).build();

        StudentProfileResponse response = studentProfileService.updateStatus(10L, request);

        assertThat(response.getStatus()).isEqualTo(StudentStatus.GRADUATED);
    }
}
