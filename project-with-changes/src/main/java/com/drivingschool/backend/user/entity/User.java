package com.drivingschool.backend.user.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
}, indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_enabled", columnList = "enabled")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // Confirmed a one-time code sent to the WhatsApp number on the user's profile.
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    // Profile photo (V23): a public Cloudinary URL, and Cloudinary's id for deleting it.
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "profile_image_public_id", length = 255)
    private String profileImagePublicId;

    // V24: INVITED until the person sets their password from the invite link.
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "invite_expires_at")
    private LocalDateTime inviteExpiresAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Exactly one permanent super-admin row - unrestricted visibility, owns no
    // school, the only account that can create/delete schools or other admins
    // directly (everyone else can only request, subject to this account's
    // approval). Enforced as "exactly one" at the DB level too (see V15's
    // partial unique index).
    @Column(name = "bootstrap_admin", nullable = false)
    private boolean bootstrapAdmin;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private StudentProfile studentProfile;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private InstructorProfile instructorProfile;

    @Builder
    public User(String email, String password, boolean enabled, boolean emailVerified) {
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.emailVerified = emailVerified;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void setProfilePhoto(String url, String publicId) {
        this.profileImageUrl = url;
        this.profileImagePublicId = publicId;
    }

    public void clearProfilePhoto() {
        this.profileImageUrl = null;
        this.profileImagePublicId = null;
    }

    public void markInvited(LocalDateTime inviteExpiresAt) {
        this.accountStatus = AccountStatus.INVITED;
        this.inviteExpiresAt = inviteExpiresAt;
    }

    public void activate() {
        this.accountStatus = AccountStatus.ACTIVE;
        this.inviteExpiresAt = null;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void verifyPhone() {
        this.phoneVerified = true;
    }

    /** Login requires this: a one-time code confirmed over email or WhatsApp. */
    public boolean isAccountVerified() {
        return emailVerified || phoneVerified;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markAsBootstrapAdmin() {
        this.bootstrapAdmin = true;
    }

    /**
     * Hides the account (blocks login via the existing enabled=false check)
     * and marks when, without touching any row that references this user -
     * bookings, quiz submissions, assessments, and lesson notes are all
     * preserved. There is no corresponding hard-delete path.
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.enabled = false;
    }

    public String getDisplayName() {
        if (studentProfile != null) {
            return studentProfile.getFirstName() + " " + studentProfile.getLastName();
        }

        if (instructorProfile != null) {
            return instructorProfile.getFirstName() + " " + instructorProfile.getLastName();
        }

        return email;
    }
}
