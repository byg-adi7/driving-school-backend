package com.drivingschool.backend.school.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schools", indexes = {
        @Index(name = "idx_schools_name", columnList = "name"),
        @Index(name = "idx_schools_active", columnList = "active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(nullable = false)
    private boolean active;

    // V25: the logo's public Cloudinary URL, and Cloudinary's id for deleting it.
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "logo_public_id", length = 255)
    private String logoPublicId;

    // Daily attendance (V20): where check-ins are measured from, how close they must be,
    // and the time zone that decides which calendar day a check-in belongs to.
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "attendance_radius_meters", nullable = false)
    private int attendanceRadiusMeters = 150;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Africa/Accra";

    // Every school has exactly one owning admin, mandatory and unique in both
    // directions - a school can't exist without its admin and an admin can't
    // own more than one school. The bootstrap admin never appears here (it
    // owns no school). Deleting this User row cascades straight through to
    // deleting the school - see SchoolAdminCascadeDeletionService.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owning_admin_id", nullable = false, unique = true)
    private User owningAdmin;

    @Builder
    public School(String name, String address, String phone, String email, boolean active, User owningAdmin) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.active = active;
        this.owningAdmin = owningAdmin;
    }

    public void update(String name, String address, String phone, String email, Boolean active) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (email != null) {
            this.email = email;
        }
        if (active != null) {
            this.active = active;
        }
    }

    public void updateAttendanceSettings(double latitude, double longitude, Integer radiusMeters, String timeZone) {
        this.latitude = latitude;
        this.longitude = longitude;
        if (radiusMeters != null) {
            this.attendanceRadiusMeters = radiusMeters;
        }
        if (timeZone != null) {
            this.timeZone = timeZone;
        }
    }

    public void setLogo(String url, String publicId) {
        this.logoUrl = url;
        this.logoPublicId = publicId;
    }

    public void clearLogo() {
        this.logoUrl = null;
        this.logoPublicId = null;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
