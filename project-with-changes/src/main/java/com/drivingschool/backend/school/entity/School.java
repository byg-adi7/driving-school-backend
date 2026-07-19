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
}
