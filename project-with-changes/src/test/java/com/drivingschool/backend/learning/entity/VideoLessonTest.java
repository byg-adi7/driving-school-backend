package com.drivingschool.backend.learning.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoLessonTest {

    @Test
    void aLessonWithoutAVideo_isMaterialsOnly() {
        VideoLesson lesson = VideoLesson.builder().title("Road signs handout").videoUrl("  ").lessonOrder(1).build();

        assertThat(lesson.getVideoUrl()).isNull();
    }

    @Test
    void updatingWithAnEmptyVideoUrl_removesTheVideo_whileNullLeavesItAlone() {
        VideoLesson lesson = VideoLesson.builder().title("Intro").videoUrl("https://video.example.com/1").lessonOrder(1).build();

        lesson.updateDetails(null, null, null, null, null);
        assertThat(lesson.getVideoUrl()).isEqualTo("https://video.example.com/1");

        lesson.updateDetails(null, null, "", null, null);
        assertThat(lesson.getVideoUrl()).isNull();
    }
}
