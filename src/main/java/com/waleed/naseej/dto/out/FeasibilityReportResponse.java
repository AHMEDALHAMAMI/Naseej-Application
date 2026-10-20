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
public class FeasibilityReportResponse {
    private Integer id;
    private Integer ideaId;
    private Integer gapScore;
    private Double memberGrowthPercent;
    private String peakDemandTime;
    private String recommendation;
    private String risks;
}
