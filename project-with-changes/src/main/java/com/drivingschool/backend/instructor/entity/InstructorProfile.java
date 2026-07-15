package com.drivingschool.backend.instructor.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "instructor_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_instructor_profiles_user_id", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_instructor_profiles_license", columnNames = "license_number")
}, indexes = {
        @Index(name = "idx_instructor_profiles_school_id", columnList = "school_id"),
        @Index(name = "idx_instructor_profiles_active", columnList = "active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorProfile extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String specialization;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder
    public InstructorProfile(String firstName, String lastName, String phone, String specialization,
                             String licenseNumber, Integer yearsExperience, String bio,
                             boolean active, School school, User user) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
        this.yearsExperience = yearsExperience;
        this.bio = bio;
        this.active = active;
        this.school = school;
        this.user = user;
    }
}
