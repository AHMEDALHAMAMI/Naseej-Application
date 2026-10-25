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

    // ---------- Filled by search and rising (lists across neighborhoods) ----------
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
}
