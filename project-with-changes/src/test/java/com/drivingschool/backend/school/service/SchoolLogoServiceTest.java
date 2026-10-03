package com.drivingschool.backend.school.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolLogoServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
    private static final byte[] SVG = "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @Mock private SchoolRepository schoolRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private ObjectProvider<Cloudinary> cloudinaryProvider;
    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    private SchoolLogoService service;
    private School school;

    @BeforeEach
    void setUp() throws Exception {
        service = new SchoolLogoService(schoolRepository, new SchoolMapper(), currentUserService, cloudinaryProvider);
        school = School.builder().name("KINGSCHOOL").address("Accra").active(true).build();
        ReflectionTestUtils.setField(school, "id", 5L);
        lenient().when(currentUserService.requireUserId()).thenReturn(9L);
        lenient().when(schoolRepository.findByOwningAdminId(9L)).thenReturn(Optional.of(school));
        lenient().when(cloudinaryProvider.getIfAvailable()).thenReturn(cloudinary);
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        lenient().when(uploader.upload(any(), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/x/image/upload/v1/school-logos/new.png",
                "public_id", "school-logos/new"));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "logo", "image/png", bytes);
    }

    @Test
    void theSchoolsAdmin_uploadsALogo_returnedAsLogoUrl() throws Exception {
        var response = service.upload(file(PNG));

        assertThat(response.getLogoUrl()).isEqualTo("https://res.cloudinary.com/x/image/upload/v1/school-logos/new.png");
        assertThat(school.getLogoPublicId()).isEqualTo("school-logos/new");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anSvgLogo_isStoredAsAPng_soNoScriptCanSurvive() throws Exception {
        service.upload(file(SVG));

        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(eq(SVG), options.capture());
        assertThat(options.getValue()).containsEntry("format", "png");
    }

    @Test
    void replacingALogo_deletesTheOldOne() throws Exception {
        school.setLogo("https://old", "school-logos/old");

        service.upload(file(PNG));

        verify(uploader).destroy(eq("school-logos/old"), anyMap());
    }

    @Test
    void anythingThatIsNotAnImage_orTooBig_isRejected() {
        assertThatThrownBy(() -> service.upload(file("%PDF-1.4".getBytes())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("JPEG, PNG, WebP or SVG");
        assertThatThrownBy(() -> service.upload(file(new byte[(int) SchoolLogoService.MAX_BYTES + 1])))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("2 MB");
    }

    @Test
    void anAdminWithoutASchool_cannotSetALogo() {
        when(schoolRepository.findByOwningAdminId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(file(PNG))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removing_clearsTheLogo_andDeletesItFromCloudinary() throws Exception {
        school.setLogo("https://old", "school-logos/old");

        assertThat(service.remove().getLogoUrl()).isNull();
        verify(uploader).destroy(eq("school-logos/old"), anyMap());
    }
}
