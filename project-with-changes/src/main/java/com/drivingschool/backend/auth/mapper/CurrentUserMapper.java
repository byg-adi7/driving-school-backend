package com.drivingschool.backend.auth.mapper;

import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class CurrentUserMapper {

    public CurrentUserResponse toResponse(User user, StudentProfile student, InstructorProfile instructor, School ownedSchool) {
        School school = student != null ? student.getSchool()
                : instructor != null ? instructor.getSchool()
                : ownedSchool;
        Long schoolId = school != null ? school.getId() : null;

        return CurrentUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()))
                .studentProfileId(student != null ? student.getId() : null)
                .instructorProfileId(instructor != null ? instructor.getId() : null)
                .schoolId(schoolId)
                .schoolName(school != null ? school.getName() : null)
                .schoolLogoUrl(school != null ? school.getLogoUrl() : null)
                .profileImageUrl(user.getProfileImageUrl())
                .accountStatus(user.getAccountStatus().name())
                .inviteExpiresAt(user.getInviteExpiresAt())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .bootstrapAdmin(user.isBootstrapAdmin())
                .build();
    }
}
