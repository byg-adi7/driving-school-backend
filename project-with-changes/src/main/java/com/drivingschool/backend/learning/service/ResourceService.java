package com.drivingschool.backend.learning.service;

import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.dto.UpdateResourceRequest;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ResourceService {

    ResourceResponse create(CreateResourceRequest request, Long userId, String role);

    void delete(Long resourceId, Long userId, String role);

    List<ResourceResponse> getByLesson(Long lessonId, Long userId, String role);

    ResourceResponse upload(Long lessonId, String title, MultipartFile file, Long userId, String role);

    ResourceResponse rename(Long resourceId, UpdateResourceRequest request, Long userId, String role);

    ResourceResponse replaceFile(Long resourceId, MultipartFile file, Long userId, String role);

    DownloadableFile download(Long resourceId, Long userId, String role);

    /** An uploaded resource's file plus the name to download it as. */
    record DownloadableFile(org.springframework.core.io.Resource content, String fileName) {
    }
}
