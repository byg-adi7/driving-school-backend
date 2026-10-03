package com.drivingschool.backend.school.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ServiceUnavailableException;
import com.drivingschool.backend.common.transaction.AfterCommit;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.storage.ImageFiles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A school's logo, set by its admin: JPEG, PNG, WebP or SVG up to 2 MB, stored on
 * Cloudinary as a public image scaled to fit 512x512 (never enlarged, never cropped).
 * An SVG is stored as a PNG: SVG files can carry scripts, so it's rasterised on upload
 * and the stored logo is always a plain image.
 */
@Slf4j
@Service
public class SchoolLogoService {

    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_SIZE = 512;

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;
    private final CurrentUserService currentUserService;
    private final ObjectProvider<Cloudinary> cloudinary;

    public SchoolLogoService(SchoolRepository schoolRepository, SchoolMapper schoolMapper,
                             CurrentUserService currentUserService, ObjectProvider<Cloudinary> cloudinary) {
        this.schoolRepository = schoolRepository;
        this.schoolMapper = schoolMapper;
        this.currentUserService = currentUserService;
        this.cloudinary = cloudinary;
    }

    @Transactional
    @CacheEvict(value = {"schools", "schools-active"}, allEntries = true)
    public SchoolResponse upload(MultipartFile file) {
        School school = mySchool();
        byte[] content = read(file);
        ImageFiles.Type type = ImageFiles.detect(content);
        if (type == null) {
            throw new BadRequestException("The logo must be a JPEG, PNG, WebP or SVG image");
        }
        Cloudinary client = cloudinary.getIfAvailable();
        if (client == null) {
            throw new ServiceUnavailableException("Logos need Cloudinary storage, which isn't configured here");
        }

        Map<String, Object> options = new HashMap<>(ObjectUtils.asMap(
                "public_id", "school-logos/school-" + school.getId() + "-" + UUID.randomUUID(),
                "resource_type", "image",
                "transformation", new Transformation<>().width(MAX_SIZE).height(MAX_SIZE).crop("limit")));
        if (type == ImageFiles.Type.SVG) {
            options.put("format", "png");
        }
        Map<?, ?> result;
        try {
            result = client.uploader().upload(content, options);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to upload logo for school {} ({})", school.getId(), e.getMessage(), e);
            throw new ServiceUnavailableException("Logo storage is unavailable right now - please try again later", e);
        }

        String previous = school.getLogoPublicId();
        school.setLogo((String) result.get("secure_url"), (String) result.get("public_id"));
        schoolRepository.save(school);
        if (previous != null) {
            AfterCommit.run("delete replaced logo " + previous, () -> destroy(client, previous));
        }
        return schoolMapper.toResponse(school);
    }

    @Transactional
    @CacheEvict(value = {"schools", "schools-active"}, allEntries = true)
    public SchoolResponse remove() {
        School school = mySchool();
        String previous = school.getLogoPublicId();
        school.clearLogo();
        schoolRepository.save(school);
        Cloudinary client = cloudinary.getIfAvailable();
        if (previous != null && client != null) {
            AfterCommit.run("delete removed logo " + previous, () -> destroy(client, previous));
        }
        return schoolMapper.toResponse(school);
    }

    private School mySchool() {
        return schoolRepository.findByOwningAdminId(currentUserService.requireUserId())
                .orElseThrow(() -> new ForbiddenException("Only a school's own admin can change its logo"));
    }

    static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a logo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("The logo must be 2 MB or smaller");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The logo could not be read");
        }
    }

    private static void destroy(Cloudinary client, String publicId) {
        try {
            client.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
