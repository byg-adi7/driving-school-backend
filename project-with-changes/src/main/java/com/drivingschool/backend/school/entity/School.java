package com.drivingschool.backend.school.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
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

    @Builder
    public School(String name, String address, String phone, String email, boolean active) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.active = active;
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
