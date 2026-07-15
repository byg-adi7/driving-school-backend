package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.school.dto.CreateSchoolRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceImplTest {

    @Mock private SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper = new SchoolMapper();

    private SchoolServiceImpl schoolService;

    @BeforeEach
    void setUp() {
        schoolService = new SchoolServiceImpl(schoolRepository, schoolMapper);
    }

    private School schoolWithId(Long id, String name, boolean active) {
        School school = School.builder().name(name).address("123 Main St").active(active).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    @Test
    void create_savesAndReturnsActiveSchool() {
        CreateSchoolRequest request = CreateSchoolRequest.builder()
                .name("Downtown Driving School").address("123 Main St").phone("555-1234").email("info@dds.example").build();
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> {
            School school = inv.getArgument(0);
            ReflectionTestUtils.setField(school, "id", 1L);
            return school;
        });

        SchoolResponse response = schoolService.create(request);

        assertThat(response.getName()).isEqualTo("Downtown Driving School");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void getById_existingSchool_returnsResponse() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(schoolWithId(1L, "Downtown", true)));

        SchoolResponse response = schoolService.getById(1L);

        assertThat(response.getName()).isEqualTo("Downtown");
    }

    @Test
    void getById_unknownSchool_throwsResourceNotFoundException() {
        when(schoolRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllActive_filtersOutInactiveSchools() {
        when(schoolRepository.findAll()).thenReturn(List.of(
                schoolWithId(1L, "Active School", true),
                schoolWithId(2L, "Inactive School", false)));

        List<SchoolResponse> result = schoolService.getAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Active School");
    }
}
