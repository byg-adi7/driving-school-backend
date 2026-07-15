package com.drivingschool.backend.student.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.enums.StudentStatus;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "student_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_profiles_user_id", columnNames = "user_id")
}, indexes = {
        @Index(name = "idx_student_profiles_school_id", columnList = "school_id"),
        @Index(name = "idx_student_profiles_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "enrollment_date", nullable = false)
    private LocalDate enrollmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudentStatus status;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder
    public StudentProfile(String firstName, String lastName, String phone, LocalDate dateOfBirth,
                          LocalDate enrollmentDate, StudentStatus status, String profileImageUrl,
                          School school, User user) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.enrollmentDate = enrollmentDate;
        this.status = status;
        this.profileImageUrl = profileImageUrl;
        this.school = school;
        this.user = user;
    }

    public void updateStatus(StudentStatus status) {
        this.status = status;
    }
}
