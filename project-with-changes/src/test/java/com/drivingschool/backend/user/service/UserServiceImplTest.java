package com.drivingschool.backend.user.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import com.drivingschool.backend.school.repository.SchoolDeletionRequestRepository;
import com.drivingschool.backend.school.service.SchoolAdminCascadeDeletionService;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private SchoolDeletionRequestRepository schoolDeletionRequestRepository;
    @Mock private SchoolAdminCascadeDeletionService cascadeDeletionService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, schoolDeletionRequestRepository, cascadeDeletionService);
    }

    private User existingUser(Long id) {
        User user = User.builder()
                .email("user@example.com")
                .password("encoded-password")
                .enabled(true)
                .emailVerified(true)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User adminUser(Long id) {
        User user = existingUser(id);
        ReflectionTestUtils.setField(user, "roles", java.util.Set.of(Role.builder().name(RoleName.ADMIN).build()));
        return user;
    }

    @Test
    void softDelete_withExistingActiveUser_marksDeletedAndDisablesLogin() {
        User user = existingUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.softDelete(1L);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.isEnabled()).isFalse();
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void softDelete_whenUserNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.softDelete(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void softDelete_whenAlreadyDeleted_throwsBadRequestException() {
        User user = existingUser(1L);
        user.softDelete();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.softDelete(1L))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    // --- deleteUserAccount ---

    @Test
    void deleteUserAccount_targetIsStudent_delegatesToSoftDelete() {
        User target = existingUser(10L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        userService.deleteUserAccount(10L, 1L);

        assertThat(target.isDeleted()).isTrue();
        verify(userRepository).save(target);
    }

    @Test
    void deleteUserAccount_targetIsBootstrapAdmin_throwsBadRequestException() {
        User target = adminUser(2L);
        ReflectionTestUtils.setField(target, "bootstrapAdmin", true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.deleteUserAccount(2L, 1L))
                .isInstanceOf(BadRequestException.class);

        verify(cascadeDeletionService, never()).execute(any());
    }

    @Test
    void deleteUserAccount_targetIsAdminAndCallerIsNotBootstrap_throwsBadRequestException() {
        User target = adminUser(3L);
        User caller = adminUser(1L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> userService.deleteUserAccount(3L, 1L))
                .isInstanceOf(BadRequestException.class);

        verify(cascadeDeletionService, never()).execute(any());
    }

    @Test
    void deleteUserAccount_targetIsAdminAndCallerIsBootstrap_invokesCascade() {
        User target = adminUser(3L);
        School owned = School.builder().name("X").address("Y").active(true).owningAdmin(target).build();
        ReflectionTestUtils.setField(owned, "id", 7L);
        ReflectionTestUtils.setField(target, "ownedSchool", owned);

        User bootstrap = adminUser(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);

        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));
        when(schoolDeletionRequestRepository.findBySchoolIdAndStatus(7L, SchoolDeletionRequestStatus.PENDING))
                .thenReturn(Optional.empty());

        userService.deleteUserAccount(3L, 1L);

        verify(cascadeDeletionService).execute(7L);
    }
}
