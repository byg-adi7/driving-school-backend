package com.drivingschool.backend.gamification.dto;

import com.drivingschool.backend.gamification.enums.BadgeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadgeResponse {

    private BadgeType badge;
    private String displayName;
    private String description;
    private LocalDateTime awardedAt;
}
