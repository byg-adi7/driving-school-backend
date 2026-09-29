package com.drivingschool.backend.messaging.repository;

import com.drivingschool.backend.messaging.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // The author's name is on every row - fetch-join the instructor.
    @Query(value = "SELECT a FROM Announcement a JOIN FETCH a.instructor WHERE a.school.id = :schoolId "
            + "ORDER BY a.createdAt DESC, a.id DESC",
            countQuery = "SELECT COUNT(a) FROM Announcement a WHERE a.school.id = :schoolId")
    Page<Announcement> findBySchoolId(@Param("schoolId") Long schoolId, Pageable pageable);

    @Query(value = "SELECT a FROM Announcement a JOIN FETCH a.instructor ORDER BY a.createdAt DESC, a.id DESC",
            countQuery = "SELECT COUNT(a) FROM Announcement a")
    Page<Announcement> findAllNewestFirst(Pageable pageable);
}
