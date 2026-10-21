package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
public class IdeaCheckRequest {

    @NotNull(message = "Idea is required")
    private Integer ideaId;

    private Integer similarIdeaId;

    @NotNull(message = "Score is required")
    @Min(value = 0, message = "Score must be between 0 and 100")
    @Max(value = 100, message = "Score must be between 0 and 100")
    private Integer score;

    @NotEmpty(message = "Decision is required")
    @Pattern(regexp = "^(FIT|SIMILAR|SATURATED)$", message = "Decision must be FIT, SIMILAR or SATURATED")
    private String decision;

    @NotNull(message = "Search radius is required")
    @Positive(message = "Search radius must be positive")
    private Double searchRadiusKm;

    @NotNull(message = "Competitors count is required")
    @PositiveOrZero(message = "Competitors count can't be negative")
    private Integer competitorsCount;

    @PositiveOrZero(message = "Distance can't be negative")
    private Double nearestCompetitorKm;

    @NotNull(message = "Scarcity result is required")
    private Boolean scarcityFound;

    private String feasibilitySummary;
}
