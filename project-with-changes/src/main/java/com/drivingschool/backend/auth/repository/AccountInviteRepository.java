package com.drivingschool.backend.auth.repository;

import com.drivingschool.backend.auth.entity.AccountInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountInviteRepository extends JpaRepository<AccountInvite, Long> {

    Optional<AccountInvite> findByTokenHash(String tokenHash);

    /** A new invite replaces the old one, so its link stops working. */
    @Modifying
    @Query("DELETE FROM AccountInvite i WHERE i.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
