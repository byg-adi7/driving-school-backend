package com.drivingschool.backend.school.repository;

import com.drivingschool.backend.school.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<School, Long> {

    // Deliberately queried on the owning FK side rather than navigating
    // User.ownedSchool (the mappedBy inverse side) - a lazy mappedBy @OneToOne
    // is never (re)populated on an already-cached/managed User instance since
    // it was only ever set on the School side, so relying on it silently
    // returns null whenever the same User object is reused within a
    // persistence context (confirmed via integration testing, not just theory).
    Optional<School> findByOwningAdminId(Long owningAdminId);
}
