package com.waleed.naseej.dto.out;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An idea in an investor's neighborhood list, search results or rising requests. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpportunityResponse {

    private Integer id;
    private String title;
    private String category;
    private String status;
    private String locationLabel;
    private Double latitude;
    private Double longitude;
    private Long votesCount;
    private Integer membershipThreshold;
    private Integer votePercent;
    private Integer gapScore;
    private Boolean unlocked;
    private Boolean canUnlock;

    // ---------- Filled by search, rising and matches (lists across neighborhoods) ----------
    private Integer neighborhoodId;
    private String neighborhoodName;
    private String city;
    // From the business model, when it exists
    private BigDecimal estimatedCapitalMin;
    private BigDecimal estimatedCapitalMax;

    // ---------- Rising only ----------
    // Votes cast in the chosen period, and how much they grew the total (percent of the votes before it)
    private Long recentVotes;
    private Integer growthPercent;

    // Confidential summary until this investor unlocks it (title is null, the point is an approximate area)
    private Boolean teaser;
    private Integer approxRadiusM;
    private Integer checkScore;
    private Integer competitorsCount;
    private Long votesLast7Days;
    private BigDecimal capitalMin;
    private BigDecimal capitalMax;
    private String stage;
    private Integer stageIndex;
    private Integer stageCount;

    // Matches for a chosen neighborhood: how far this opportunity is from it
    private Double distanceKm;

    // How the estimated capital sits in the investor's budget range: FULL, PARTIAL or UNKNOWN (no estimate yet).
    // Null when no budget was given. Opportunities with no overlap are left out by the server.
    private String budgetFit;

    // AI search: why this opportunity matches what the investor asked for
    private String aiReason;
}
