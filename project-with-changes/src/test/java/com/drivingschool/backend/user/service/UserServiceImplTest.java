package com.drivingschool.backend.user.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository);
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

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void softDelete_whenAlreadyDeleted_throwsBadRequestException() {
        User user = existingUser(1L);
        user.softDelete();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.softDelete(1L))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
