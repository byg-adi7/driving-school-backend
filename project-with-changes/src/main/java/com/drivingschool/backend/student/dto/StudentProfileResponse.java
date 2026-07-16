package com.drivingschool.backend.student.dto;

import com.drivingschool.backend.student.enums.StudentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class StudentProfileResponse {

    private final Long id;
    private final Long userId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final LocalDate dateOfBirth;
    private final LocalDate enrollmentDate;
    private final StudentStatus status;
    private final String profileImageUrl;
    private final Long schoolId;
    private final String schoolName;
}
