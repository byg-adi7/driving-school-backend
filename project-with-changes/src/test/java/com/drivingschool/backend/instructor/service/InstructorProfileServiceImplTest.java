package com.drivingschool.backend.instructor.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.dto.InstructorProfileResponse;
import com.drivingschool.backend.instructor.dto.UpdateInstructorActiveStatusRequest;
import com.drivingschool.backend.instructor.dto.UpdateInstructorProfileRequest;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.mapper.InstructorProfileMapper;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructorProfileServiceImplTest {

    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private CurrentUserService currentUserService;
    private final InstructorProfileMapper instructorProfileMapper = new InstructorProfileMapper();

    private InstructorProfileServiceImpl instructorProfileService;

    @BeforeEach
    void setUp() {
        instructorProfileService = new InstructorProfileServiceImpl(
                instructorProfileRepository, instructorProfileMapper, currentUserService);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("instructor@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private School schoolWithId(Long id) {
        School school = School.builder().name("Test School").address("1 Main St").active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private InstructorProfile profileWithId(Long id, User user, School school) {
        InstructorProfile profile = InstructorProfile.builder()
                .firstName("Jane").lastName("Doe").phone("123").specialization("Highway")
                .licenseNumber("LIC1").yearsExperience(5).bio("Bio").active(true)
                .school(school).user(user).build();
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    @Test
    void getMyProfile_whenProfileExists_returnsMappedResponse() {
        User user = userWithId(1L);
        School school = schoolWithId(1L);
        InstructorProfile profile = profileWithId(10L, user, school);
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        InstructorProfileResponse response = instructorProfileService.getMyProfile();

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getEmail()).isEqualTo("instructor@example.com");
    }

    @Test
    void getMyProfile_whenProfileMissing_throwsResourceNotFoundException() {
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instructorProfileService.getMyProfile())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMyProfile_withValidRequest_updatesEditableFields() {
        User user = userWithId(1L);
        School school = schoolWithId(1L);
        InstructorProfile profile = profileWithId(10L, user, school);
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(instructorProfileRepository.save(any(InstructorProfile.class))).thenReturn(profile);
        UpdateInstructorProfileRequest request = UpdateInstructorProfileRequest.builder()
                .firstName("Janet").lastName("Smith").phone("999")
                .specialization("Parallel Parking").yearsExperience(10).bio("New bio").build();

        InstructorProfileResponse response = instructorProfileService.updateMyProfile(request);

        assertThat(response.getFirstName()).isEqualTo("Janet");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getYearsExperience()).isEqualTo(10);
        assertThat(response.getLicenseNumber()).isEqualTo("LIC1");
    }

    @Test
    void getBySchool_returnsAllInstructorsForSchool() {
        User user1 = userWithId(1L);
        User user2 = userWithId(2L);
        School school = schoolWithId(1L);
        InstructorProfile p1 = profileWithId(10L, user1, school);
        InstructorProfile p2 = profileWithId(11L, user2, school);
        when(instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(1L)).thenReturn(List.of(p1, p2));

        List<InstructorProfileResponse> responses = instructorProfileService.getBySchool(1L);

        assertThat(responses).hasSize(2);
    }

    @Test
    void updateActiveStatus_whenNotFound_throwsResourceNotFoundException() {
        when(instructorProfileRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateInstructorActiveStatusRequest request = UpdateInstructorActiveStatusRequest.builder().active(false).build();

        assertThatThrownBy(() -> instructorProfileService.updateActiveStatus(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateActiveStatus_withValidRequest_updatesActiveFlag() {
        User user = userWithId(1L);
        School school = schoolWithId(1L);
        InstructorProfile profile = profileWithId(10L, user, school);
        when(instructorProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(instructorProfileRepository.save(any(InstructorProfile.class))).thenReturn(profile);
        UpdateInstructorActiveStatusRequest request = UpdateInstructorActiveStatusRequest.builder().active(false).build();

        InstructorProfileResponse response = instructorProfileService.updateActiveStatus(10L, request);

        assertThat(response.isActive()).isFalse();
    }
}
