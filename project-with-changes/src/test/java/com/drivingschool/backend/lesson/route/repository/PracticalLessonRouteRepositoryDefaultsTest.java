package com.drivingschool.backend.lesson.route.repository;

import com.drivingschool.backend.lesson.route.entity.PracticalLessonRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class PracticalLessonRouteRepositoryDefaultsTest {

    @Test
    void findByBookingId_withSeveralRoutesFromBeforeReplacement_returnsTheNewestInsteadOfFailing() {
        PracticalLessonRouteRepository repository = mock(PracticalLessonRouteRepository.class, CALLS_REAL_METHODS);
        PracticalLessonRoute newest = PracticalLessonRoute.builder().startLocation("newest").build();
        PracticalLessonRoute older = PracticalLessonRoute.builder().startLocation("older").build();
        doReturn(List.of(newest, older)).when(repository).findAllByBookingIdNewestFirst(2L);

        assertThat(repository.findByBookingId(2L)).containsSame(newest);
    }
}
