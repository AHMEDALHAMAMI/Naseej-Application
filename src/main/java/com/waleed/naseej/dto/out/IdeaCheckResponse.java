package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdeaCheckResponse {
    private Integer id;
    private Integer ideaId;
    private Integer similarIdeaId;
    private Integer score;
    private String decision;
    private Double searchRadiusKm;
    private Integer competitorsCount;
    private Double nearestCompetitorKm;
    private Boolean scarcityFound;
    private String feasibilitySummary;
    private String placeType;
}
