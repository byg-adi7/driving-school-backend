package com.drivingschool.backend.auth.mapper;

import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class CurrentUserMapper {

    public CurrentUserResponse toResponse(User user, StudentProfile student, InstructorProfile instructor) {
        Long schoolId = null;
        if (student != null) {
            schoolId = student.getSchool().getId();
        } else if (instructor != null) {
            schoolId = instructor.getSchool().getId();
        }

        return CurrentUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()))
                .studentProfileId(student != null ? student.getId() : null)
                .instructorProfileId(instructor != null ? instructor.getId() : null)
                .schoolId(schoolId)
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .build();
    }
}
