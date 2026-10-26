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
    private Integer shortageScore;
    private Integer competitionScore;
    private Integer demandScore;
    private String decision;
    private Double searchRadiusKm;
    private Integer competitorsCount;
    private Double nearestCompetitorKm;
    private Boolean scarcityFound;
    private String feasibilitySummary;
    private String placeType;
    // Only set right after a check: true when the map service didn't answer and the AI judged without competitors
    private Boolean competitorsUnavailable;
}
