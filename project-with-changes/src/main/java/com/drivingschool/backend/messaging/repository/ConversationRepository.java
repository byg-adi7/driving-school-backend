package com.drivingschool.backend.messaging.repository;

import com.drivingschool.backend.messaging.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);

    // Both participants (and their users) are dereferenced for every row - the
    // counterpart's name and the participant check - so fetch-join them here.
    @Query("SELECT c FROM Conversation c JOIN FETCH c.student s JOIN FETCH s.user su "
            + "JOIN FETCH c.instructor i JOIN FETCH i.user iu WHERE c.id = :id")
    Optional<Conversation> findWithParticipantsById(@Param("id") Long id);

    @Query("SELECT c FROM Conversation c JOIN FETCH c.student s JOIN FETCH s.user su "
            + "JOIN FETCH c.instructor i JOIN FETCH i.user iu "
            + "WHERE su.id = :userId OR iu.id = :userId "
            + "ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC")
    List<Conversation> findAllForUser(@Param("userId") Long userId);
}
