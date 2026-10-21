package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class FeasibilityReportRequest {

    @NotNull(message = "Idea is required")
    private Integer ideaId;

    @NotNull(message = "Gap score is required")
    @Min(value = 0, message = "Gap score must be between 0 and 100")
    @Max(value = 100, message = "Gap score must be between 0 and 100")
    private Integer gapScore;

    private Double memberGrowthPercent;

    @Size(max = 50, message = "Peak demand time must be at most 50 characters")
    private String peakDemandTime;

    @NotEmpty(message = "Recommendation is required")
    private String recommendation;

    private String risks;
}
