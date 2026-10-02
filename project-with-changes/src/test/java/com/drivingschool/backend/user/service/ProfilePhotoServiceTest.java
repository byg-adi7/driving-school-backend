package com.drivingschool.backend.user.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ServiceUnavailableException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilePhotoServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    @Mock private UserRepository userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private AdminSchoolScope adminSchoolScope;
    @Mock private ObjectProvider<Cloudinary> cloudinaryProvider;
    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    private ProfilePhotoService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProfilePhotoService(userRepository, studentProfileRepository, instructorProfileRepository,
                currentUserService, adminSchoolScope, cloudinaryProvider);
        lenient().when(cloudinaryProvider.getIfAvailable()).thenReturn(cloudinary);
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        lenient().when(uploader.upload(any(), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/x/image/upload/v1/profile-photos/new.jpg",
                "public_id", "profile-photos/new"));
    }

    private User user(Long id) {
        User u = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(u, "id", id);
        lenient().when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private School school(Long id) {
        School s = School.builder().name("S").address("A").active(true).build();
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private void callerIs(Long userId, RoleName role) {
        lenient().when(currentUserService.requireUserId()).thenReturn(userId);
        lenient().when(currentUserService.hasRole(any())).thenAnswer(inv -> inv.getArgument(0) == role);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "me.jpg", "image/jpeg", bytes);
    }

    @Test
    void anyoneCanSetTheirOwnPhoto_whichIsStoredCroppedAndPublic() throws Exception {
        User me = user(2L);
        callerIs(2L, RoleName.STUDENT);

        var response = service.upload(null, file(JPEG));

        assertThat(response.profileImageUrl()).isEqualTo("https://res.cloudinary.com/x/image/upload/v1/profile-photos/new.jpg");
        assertThat(me.getProfileImagePublicId()).isEqualTo("profile-photos/new");
        verify(uploader).upload(eq(JPEG), anyMap());
    }

    @Test
    void replacingAPhoto_deletesTheOldOneFromCloudinary() throws Exception {
        User me = user(2L);
        me.setProfilePhoto("https://old", "profile-photos/old");
        callerIs(2L, RoleName.INSTRUCTOR);

        service.upload(null, file(PNG));

        verify(uploader).destroy(eq("profile-photos/old"), anyMap());
    }

    @Test
    void pngAndWebp_areAccepted_butAnythingElseIsRejectedByItsContent() {
        assertThat(ProfilePhotoService.validate(file(PNG))).isEqualTo(PNG);
        assertThat(ProfilePhotoService.validate(file(WEBP))).isEqualTo(WEBP);
        assertThatThrownBy(() -> ProfilePhotoService.validate(file("%PDF-1.4".getBytes())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("JPEG, PNG or WebP");
        assertThatThrownBy(() -> ProfilePhotoService.validate(file(new byte[(int) ProfilePhotoService.MAX_BYTES + 1])))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("5 MB");
    }

    @Test
    void anInstructor_canSetTheirStudentsPhoto_butNotAnotherSchoolsOrAnInstructors() {
        user(2L);
        user(7L);
        user(8L);
        callerIs(3L, RoleName.INSTRUCTOR);
        when(instructorProfileRepository.findByUserId(3L))
                .thenReturn(Optional.of(InstructorProfile.builder().school(school(5L)).build()));
        when(studentProfileRepository.findByUserId(2L))
                .thenReturn(Optional.of(StudentProfile.builder().school(school(5L)).build()));
        when(studentProfileRepository.findByUserId(7L))
                .thenReturn(Optional.of(StudentProfile.builder().school(school(6L)).build()));
        when(studentProfileRepository.findByUserId(8L)).thenReturn(Optional.empty());

        assertThat(service.upload(2L, file(JPEG)).profileImageUrl()).isNotNull();
        assertThatThrownBy(() -> service.upload(7L, file(JPEG))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.upload(8L, file(JPEG))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aStudent_cannotSetSomeoneElsesPhoto() throws Exception {
        user(4L);
        callerIs(2L, RoleName.STUDENT);

        assertThatThrownBy(() -> service.upload(4L, file(JPEG))).isInstanceOf(ForbiddenException.class);
        verify(uploader, never()).upload(any(), anyMap());
    }

    @Test
    void anAdmin_isLimitedToAccountsOfTheirSchool() {
        user(4L);
        callerIs(9L, RoleName.ADMIN);
        doThrow(new ForbiddenException("no")).when(adminSchoolScope).requireAccessToUser(4L);

        assertThatThrownBy(() -> service.upload(4L, file(JPEG))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removing_clearsThePhoto_andDeletesItFromCloudinary() throws Exception {
        User me = user(2L);
        me.setProfilePhoto("https://old", "profile-photos/old");
        callerIs(2L, RoleName.STUDENT);

        assertThat(service.remove(null).profileImageUrl()).isNull();
        assertThat(me.getProfileImageUrl()).isNull();
        verify(uploader).destroy(eq("profile-photos/old"), anyMap());
    }

    @Test
    void withoutCloudinary_uploadingIsServiceUnavailable() {
        user(2L);
        callerIs(2L, RoleName.STUDENT);
        when(cloudinaryProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.upload(null, file(JPEG))).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void aCloudinaryFailure_isServiceUnavailable_andThePhotoIsUnchanged() throws Exception {
        User me = user(2L);
        callerIs(2L, RoleName.STUDENT);
        when(uploader.upload(any(), anyMap())).thenThrow(new RuntimeException("Invalid cloud_name"));

        assertThatThrownBy(() -> service.upload(null, file(JPEG))).isInstanceOf(ServiceUnavailableException.class);
        assertThat(me.getProfileImageUrl()).isNull();
        verify(userRepository, never()).save(any());
    }
}
