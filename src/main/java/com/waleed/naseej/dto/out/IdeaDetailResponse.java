package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.InvestorStatus;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Idea page. What is filled depends on who is looking: residents never get vote counts or the business model. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdeaDetailResponse {

    private Integer id;
    private String title;
    private String description;
    private String category;
    private String status;
    private Integer neighborhoodId;
    private String neighborhoodName;
    private String city;
    private Double latitude;
    private Double longitude;
    // e.g. "North side, 0.8 km from the center"
    private String locationLabel;
    private LocalDateTime publishedAt;
    private LocalDateTime confirmedAt;

    // FOUNDER, VOTER (resident), INVESTOR or ADMIN
    private String viewerRole;
    private Boolean founder;

    // ---------- Voting (resident) ----------
    private Double votingRadiusKm;
    private Double distanceKm;
    private Boolean inRange;
    private Boolean canVote;
    private Boolean voted;

    // ---------- Founder, investor and admin only ----------
    private Long votesCount;
    private Integer membershipThreshold;
    private Integer votePercent;
    private BusinessModelResponse businessModel;

    // ---------- AI check ----------
    private String checkDecision;
    private Integer checkScore;
    private Boolean scarcityFound;
    private Double searchRadiusKm;
    private Integer competitorsCount;
    private Double nearestCompetitorKm;
    private String feasibilitySummary;
    private List<NearbyPlaceResponse> competitors;
    private String rejectionReason;
    private Integer similarIdeaId;
    private String similarIdeaTitle;

    // ---------- Investor only ----------
    private InvestorStatus investorStatus;
    private Integer gapScore;
    private Boolean unlocked;
    private Boolean adopted;
    private Boolean canUnlock;
    private Boolean canAdopt;
    private String unlockBlockedReason;
    // Full report only after unlocking (or for the idea's own investor)
    private FeasibilityReportResponse feasibilityReport;

    // Steps that didn't finish, e.g. the AI couldn't write the business model this time
    private List<String> warnings;
}
