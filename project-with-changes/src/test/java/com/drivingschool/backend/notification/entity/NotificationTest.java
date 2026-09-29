package com.drivingschool.backend.notification.entity;

import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private Notification withSubject(String subject) {
        return Notification.builder().subject(subject).body("b")
                .channel(NotificationChannel.IN_APP).status(NotificationStatus.PENDING).build();
    }

    @Test
    void aSubjectLongerThanTheColumn_isTrimmedWithAnEllipsisInsteadOfFailingTheInsert() {
        // e.g. "Announcement from <long name>: <200-character subject>"
        Notification notification = withSubject("Announcement from Ina Instructor: " + "x".repeat(300));

        assertThat(notification.getSubject()).hasSize(Notification.MAX_SUBJECT_LENGTH).endsWith("...");
    }

    @Test
    void aSubjectThatFits_isUnchanged() {
        assertThat(withSubject("Lesson booked").getSubject()).isEqualTo("Lesson booked");
    }
}
