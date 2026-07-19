package com.drivingschool.backend.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntryResponse {

    private Integer rank;
    private Long studentId;
    private String studentName;
    private Integer totalPoints;
    private Integer currentStreakWeeks;
}
