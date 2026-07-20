package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolDeletionRequestRepository;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.SchoolAccessValidator;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceImplTest {

    @Mock private SchoolRepository schoolRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentUserService currentUserService;
    @Mock private SchoolDeletionRequestRepository schoolDeletionRequestRepository;
    @Mock private SchoolAdminCascadeDeletionService cascadeDeletionService;
    @Mock private NotificationService notificationService;
    private final SchoolMapper schoolMapper = new SchoolMapper();
    private SchoolAccessValidator accessValidator;

    private SchoolServiceImpl schoolService;

    @BeforeEach
    void setUp() {
        accessValidator = new SchoolAccessValidator(schoolRepository);
        schoolService = new SchoolServiceImpl(schoolRepository, schoolMapper, userRepository, roleRepository,
                passwordEncoder, currentUserService, schoolDeletionRequestRepository, cascadeDeletionService,
                accessValidator, notificationService);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("admin" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private School schoolWithId(Long id, String name, boolean active, User owningAdmin) {
        School school = School.builder().name(name).address("123 Main St").active(active).owningAdmin(owningAdmin).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private CreateSchoolWithAdminRequest createRequest() {
        return CreateSchoolWithAdminRequest.builder()
                .schoolName("Downtown Driving School").schoolAddress("123 Main St")
                .adminEmail("owner@dds.example").adminPassword("SecurePass123!").build();
    }

    // --- createWithAdmin ---

    @Test
    void createWithAdmin_asBootstrap_savesSchoolAndAdmin() {
        when(currentUserService.isBootstrapAdmin()).thenReturn(true);
        when(userRepository.existsByEmail("owner@dds.example")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(Role.builder().name(RoleName.ADMIN).build()));
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 50L);
            return u;
        });
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 1L);
            return s;
        });

        SchoolWithAdminResponse response = schoolService.createWithAdmin(createRequest());

        assertThat(response.getSchool().getName()).isEqualTo("Downtown Driving School");
        assertThat(response.getAdminUserId()).isEqualTo(50L);
        assertThat(response.getAdminEmail()).isEqualTo("owner@dds.example");
    }

    @Test
    void createWithAdmin_asNonBootstrap_throwsBadRequestException() {
        when(currentUserService.isBootstrapAdmin()).thenReturn(false);

        assertThatThrownBy(() -> schoolService.createWithAdmin(createRequest()))
                .isInstanceOf(BadRequestException.class);

        verify(schoolRepository, never()).save(any());
    }

    @Test
    void createWithAdmin_whenAdminEmailTaken_throwsBadRequestException() {
        when(currentUserService.isBootstrapAdmin()).thenReturn(true);
        when(userRepository.existsByEmail("owner@dds.example")).thenReturn(true);

        assertThatThrownBy(() -> schoolService.createWithAdmin(createRequest()))
                .isInstanceOf(BadRequestException.class);

        verify(schoolRepository, never()).save(any());
    }

    // --- getById ---

    @Test
    void getById_asInstructor_skipsOwnershipCheck() {
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(schoolWithId(1L, "Downtown", true, null)));

        SchoolResponse response = schoolService.getById(1L, 999L, "INSTRUCTOR");

        assertThat(response.getName()).isEqualTo("Downtown");
    }

    @Test
    void getById_asBootstrapAdmin_returnsAnySchool() {
        User bootstrap = userWithId(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(schoolWithId(1L, "Downtown", true, userWithId(50L))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));

        SchoolResponse response = schoolService.getById(1L, 1L, "ADMIN");

        assertThat(response.getName()).isEqualTo("Downtown");
    }

    @Test
    void getById_asOwningAdmin_returnsSchool() {
        User owner = userWithId(50L);
        School school = schoolWithId(1L, "Downtown", true, owner);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(50L)).thenReturn(Optional.of(owner));
        when(schoolRepository.findByOwningAdminId(50L)).thenReturn(Optional.of(school));

        SchoolResponse response = schoolService.getById(1L, 50L, "ADMIN");

        assertThat(response.getName()).isEqualTo("Downtown");
    }

    @Test
    void getById_asDifferentAdmin_throwsBadRequestException() {
        User owner = userWithId(50L);
        User otherAdmin = userWithId(51L);
        School school = schoolWithId(1L, "Downtown", true, owner);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(userRepository.findById(51L)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> schoolService.getById(1L, 51L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getById_unknownSchool_throwsResourceNotFoundException() {
        when(schoolRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.getById(99L, 1L, "INSTRUCTOR"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getAllActive ---

    @Test
    void getAllActive_asInstructor_returnsAllActiveSchools() {
        when(schoolRepository.findAll()).thenReturn(List.of(
                schoolWithId(1L, "Active School", true, null),
                schoolWithId(2L, "Inactive School", false, null)));

        List<SchoolResponse> result = schoolService.getAllActive(999L, "INSTRUCTOR");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Active School");
    }

    @Test
    void getAllActive_asBootstrapAdmin_returnsAllActiveSchools() {
        User bootstrap = userWithId(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));
        when(schoolRepository.findAll()).thenReturn(List.of(
                schoolWithId(1L, "School A", true, userWithId(50L)),
                schoolWithId(2L, "School B", true, userWithId(51L))));

        List<SchoolResponse> result = schoolService.getAllActive(1L, "ADMIN");

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllActive_asNonBootstrapAdmin_returnsOnlyOwnSchool() {
        User owner = userWithId(50L);
        School owned = schoolWithId(1L, "My School", true, owner);
        when(userRepository.findById(50L)).thenReturn(Optional.of(owner));
        when(schoolRepository.findByOwningAdminId(50L)).thenReturn(Optional.of(owned));

        List<SchoolResponse> result = schoolService.getAllActive(50L, "ADMIN");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("My School");
    }

    // --- deleteDirectly ---

    @Test
    void deleteDirectly_asBootstrap_invokesCascade() {
        when(currentUserService.isBootstrapAdmin()).thenReturn(true);
        when(schoolRepository.existsById(1L)).thenReturn(true);
        when(schoolDeletionRequestRepository.findBySchoolIdAndStatus(any(), any())).thenReturn(Optional.empty());

        schoolService.deleteDirectly(1L);

        verify(cascadeDeletionService).execute(1L);
    }

    @Test
    void deleteDirectly_asNonBootstrap_throwsBadRequestException() {
        when(currentUserService.isBootstrapAdmin()).thenReturn(false);

        assertThatThrownBy(() -> schoolService.deleteDirectly(1L))
                .isInstanceOf(BadRequestException.class);

        verify(cascadeDeletionService, never()).execute(any());
    }
}
