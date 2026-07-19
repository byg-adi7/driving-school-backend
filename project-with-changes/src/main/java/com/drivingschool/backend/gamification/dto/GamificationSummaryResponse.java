package com.drivingschool.backend.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamificationSummaryResponse {

    private Long studentId;
    private String studentName;
    private Integer totalPoints;
    private Integer currentStreakWeeks;
    private Integer longestStreakWeeks;
    /** 1-based rank within the student's school. */
    private Integer schoolRank;
    private List<BadgeResponse> badges;
}
