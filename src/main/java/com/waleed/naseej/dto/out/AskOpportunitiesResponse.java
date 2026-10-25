package com.waleed.naseej.dto.out;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How the AI understood the investor's question, and the opportunities that match it. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AskOpportunitiesResponse {

    // One short sentence, in the question's language, saying what was searched for
    private String summary;

    private String city;
    private Integer neighborhoodId;
    private String neighborhoodName;
    private String category;
    private BigDecimal minCapital;
    private BigDecimal maxCapital;
    private Boolean readyOnly;
    private String keywords;

    // True when nothing matched every filter, so some were dropped to show close results
    private Boolean relaxed;

    private List<OpportunityResponse> results;
}
