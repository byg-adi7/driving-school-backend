package com.drivingschool.backend.user.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.common.exception.ServiceUnavailableException;
import com.drivingschool.backend.common.transaction.AfterCommit;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.storage.ImageFiles;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Profile photos for every account, stored on Cloudinary as public images, cropped to a
 * 400x400 square on upload. Unlike course PDFs these are meant to be shown anywhere the
 * person appears, so the URL is public.
 */
@Slf4j
@Service
public class ProfilePhotoService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int SIZE = 400;
    private static final String FOLDER = "profile-photos";

    /** What the frontend shows; null when there's no photo. */
    public record PhotoResponse(Long userId, String profileImageUrl) {
    }

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final CurrentUserService currentUserService;
    private final AdminSchoolScope adminSchoolScope;
    private final ObjectProvider<Cloudinary> cloudinary;

    public ProfilePhotoService(UserRepository userRepository,
                               StudentProfileRepository studentProfileRepository,
                               InstructorProfileRepository instructorProfileRepository,
                               CurrentUserService currentUserService,
                               AdminSchoolScope adminSchoolScope,
                               ObjectProvider<Cloudinary> cloudinary) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.currentUserService = currentUserService;
        this.adminSchoolScope = adminSchoolScope;
        this.cloudinary = cloudinary;
    }

    /** userId null = the caller's own photo. */
    @Transactional
    public PhotoResponse upload(Long userId, MultipartFile file) {
        User target = findTarget(userId);
        byte[] content = validate(file);
        Cloudinary client = client();

        String publicId = FOLDER + "/user-" + target.getId() + "-" + UUID.randomUUID();
        Map<?, ?> result;
        try {
            result = client.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "image",
                    // Stored already cropped, so every client gets the same small square.
                    "transformation", new Transformation<>().width(SIZE).height(SIZE).crop("fill").gravity("center")));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to upload profile photo for user {} ({})", target.getId(), e.getMessage(), e);
            throw new ServiceUnavailableException("Photo storage is unavailable right now - please try again later", e);
        }

        String previous = target.getProfileImagePublicId();
        target.setProfilePhoto((String) result.get("secure_url"), (String) result.get("public_id"));
        userRepository.save(target);
        if (previous != null) {
            AfterCommit.run("delete replaced profile photo " + previous, () -> destroy(client, previous));
        }
        return new PhotoResponse(target.getId(), target.getProfileImageUrl());
    }

    @Transactional
    public PhotoResponse remove(Long userId) {
        User target = findTarget(userId);
        String previous = target.getProfileImagePublicId();
        target.clearProfilePhoto();
        userRepository.save(target);
        if (previous != null) {
            Cloudinary client = cloudinary.getIfAvailable();
            if (client != null) {
                AfterCommit.run("delete removed profile photo " + previous, () -> destroy(client, previous));
            }
        }
        return new PhotoResponse(target.getId(), null);
    }

    // Yourself; an admin for accounts of their school (bootstrap admin: anyone); an
    // instructor for students of their school.
    private User findTarget(Long userId) {
        Long callerId = currentUserService.requireUserId();
        Long targetId = userId != null ? userId : callerId;
        User target = userRepository.findById(targetId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetId));
        if (targetId.equals(callerId)) {
            return target;
        }
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            adminSchoolScope.requireAccessToUser(targetId);
            return target;
        }
        if (currentUserService.hasRole(RoleName.INSTRUCTOR)) {
            Long mySchool = instructorProfileRepository.findByUserId(callerId).map(p -> p.getSchool().getId()).orElse(null);
            boolean myStudent = studentProfileRepository.findByUserId(targetId)
                    .map(StudentProfile::getSchool).map(s -> s.getId().equals(mySchool)).orElse(false);
            if (myStudent) {
                return target;
            }
        }
        throw new ForbiddenException("You can't change this person's photo");
    }

    // The file's own bytes decide the type - the name and Content-Type are only claims.
    static byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a photo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("The photo must be 5 MB or smaller");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The photo could not be read");
        }
        ImageFiles.Type type = ImageFiles.detect(content);
        if (type == null || type == ImageFiles.Type.SVG) {
            throw new BadRequestException("The photo must be a JPEG, PNG or WebP image");
        }
        return content;
    }

    private Cloudinary client() {
        Cloudinary client = cloudinary.getIfAvailable();
        if (client == null) {
            throw new ServiceUnavailableException("Profile photos need Cloudinary storage, which isn't configured here");
        }
        return client;
    }

    private static void destroy(Cloudinary client, String publicId) {
        try {
            client.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
