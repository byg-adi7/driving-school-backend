package com.drivingschool.backend.lesson.note.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for a StackOverflowError: LessonNote and LessonNoteAttachment both
 * use Lombok's @Data, and their bidirectional relationship (LessonNote.attachments /
 * LessonNoteAttachment.lessonNote) recursed infinitely through generated toString()
 * methods until @ToString.Exclude was added to both sides. See PRODUCTION_READINESS.md.
 */
class LessonNoteToStringTest {

    @Test
    void toString_withBidirectionalRelationshipPopulated_doesNotRecurseInfinitely() {
        LessonNote note = LessonNote.builder()
                .lessonSummary("Good progress on parallel parking today")
                .strengths("Great mirror checks and steering control")
                .weaknesses("Needs work on reverse parking speed")
                .recommendations("Practice reverse parking twice more before test")
                .build();
        LessonNoteAttachment attachment = LessonNoteAttachment.builder()
                .lessonNote(note)
                .fileName("report.pdf")
                .fileSize(123L)
                .fileType("application/pdf")
                .filePath("lesson-notes/2026/01/01/1_uuid.pdf")
                .build();
        note.addAttachment(attachment);

        assertThatCode(note::toString).doesNotThrowAnyException();
        assertThatCode(attachment::toString).doesNotThrowAnyException();
    }
}
