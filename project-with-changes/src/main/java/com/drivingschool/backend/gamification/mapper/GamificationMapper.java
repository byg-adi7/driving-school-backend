package com.drivingschool.backend.gamification.mapper;

import com.drivingschool.backend.gamification.dto.BadgeResponse;
import com.drivingschool.backend.gamification.dto.GamificationSummaryResponse;
import com.drivingschool.backend.gamification.dto.LeaderboardEntryResponse;
import com.drivingschool.backend.gamification.entity.BadgeAward;
import com.drivingschool.backend.gamification.entity.StudentGameStats;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GamificationMapper {

    public GamificationSummaryResponse toSummaryResponse(StudentGameStats stats, List<BadgeAward> badges, int schoolRank) {
        return GamificationSummaryResponse.builder()
                .studentId(stats.getStudent().getId())
                .studentName(stats.getStudent().getFirstName() + " " + stats.getStudent().getLastName())
                .profileImageUrl(stats.getStudent().getUser().getProfileImageUrl())
                .totalPoints(stats.getTotalPoints())
                .currentStreakWeeks(stats.getCurrentStreakWeeks())
                .longestStreakWeeks(stats.getLongestStreakWeeks())
                .schoolRank(schoolRank)
                .badges(badges.stream().map(this::toBadgeResponse).toList())
                .build();
    }

    public BadgeResponse toBadgeResponse(BadgeAward award) {
        return BadgeResponse.builder()
                .badge(award.getBadge())
                .displayName(award.getBadge().getDisplayName())
                .description(award.getBadge().getDescription())
                .awardedAt(award.getCreatedAt())
                .build();
    }

    public LeaderboardEntryResponse toLeaderboardEntry(StudentGameStats stats, int rank) {
        return LeaderboardEntryResponse.builder()
                .rank(rank)
                .studentId(stats.getStudent().getId())
                .studentName(stats.getStudent().getFirstName() + " " + stats.getStudent().getLastName())
                .profileImageUrl(stats.getStudent().getUser().getProfileImageUrl())
                .totalPoints(stats.getTotalPoints())
                .currentStreakWeeks(stats.getCurrentStreakWeeks())
                .build();
    }
}
