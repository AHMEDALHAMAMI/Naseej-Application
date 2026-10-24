package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An idea in an investor's neighborhood list. */
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
}
