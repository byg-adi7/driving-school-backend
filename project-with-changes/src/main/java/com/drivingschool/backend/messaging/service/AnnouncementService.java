package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.messaging.dto.AnnouncementResponse;
import com.drivingschool.backend.messaging.dto.CreateAnnouncementRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnnouncementService {

    AnnouncementResponse create(CreateAnnouncementRequest request);

    Page<AnnouncementResponse> listForMySchool(Pageable pageable);
}
