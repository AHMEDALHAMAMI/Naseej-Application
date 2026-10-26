package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.InvestorStatus;
import java.math.BigDecimal;
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

    // Adopted ideas: the project stage the investor reached, e.g. "Fit-out" (index 3 of 7)
    private String stage;
    private Integer stageIndex;
    private Integer stageCount;

    // FOUNDER, VOTER (resident), INVESTOR or ADMIN
    private String viewerRole;
    private Boolean founder;

    // ---------- Voting (resident) ----------
    private Double votingRadiusKm;
    private Double distanceKm;
    private Boolean inRange;
    private Boolean canVote;
    private Boolean voted;
    // A resident can take the vote back while the idea is still collecting votes
    private Boolean canUnvote;
    // ---------- Founder actions ----------
    private Boolean canEdit;
    private Boolean canDelete;
    private Boolean canReviseBusinessModel;
    // Residents: the saved address this idea is measured from, and whether their address still needs confirming
    private Integer voteAddressId;
    private String voteAddressLabel;
    private Boolean addressNeedsConfirmation;
    private Boolean hasAddress;

    // ---------- Founder, investor and admin only ----------
    private Long votesCount;
    private Integer membershipThreshold;
    private Integer votePercent;
    private BusinessModelResponse businessModel;

    // ---------- AI check ----------
    private String checkDecision;
    private Integer checkScore;
    // The three parts of checkScore (null for ideas checked before the parts existed)
    private Integer shortageScore;
    private Integer competitionScore;
    private Integer demandScore;
    private Boolean scarcityFound;
    private Double searchRadiusKm;
    private Integer competitorsCount;
    private Double nearestCompetitorKm;
    private String feasibilitySummary;
    private List<NearbyPlaceResponse> competitors;
    private String rejectionReason;

    // Review before publishing (author only): a FIT draft can be published while its check is fresh,
    // and the idea can be edited and checked again a limited number of times
    private Boolean canPublish;
    private Boolean checkStale;
    private LocalDateTime checkedAt;
    private Integer rechecksLeft;

    private Integer similarIdeaId;
    private String similarIdeaTitle;

    // ---------- Investor only ----------
    // Before unlocking, an investor sees a confidential summary: no title, description, exact spot,
    // business model or competitor names; latitude/longitude are an approximate area of this radius
    private Boolean teaser;
    private Integer approxRadiusM;
    private BigDecimal capitalMin;
    private BigDecimal capitalMax;
    private Long votesLast7Days;
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
