package com.waleed.naseej.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdeaCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // FK -> Idea.id (one check per idea)
    @NotNull(message = "Idea id is required")
    @Column(name = "idea_id", nullable = false, unique = true)
    private Integer ideaId;

    // FK -> Idea.id of a published idea that looks the same (null if none)
    @Column(name = "similar_idea_id")
    private Integer similarIdeaId;

    @NotNull(message = "Score is required")
    @Min(value = 0, message = "Score must be between 0 and 100")
    @Max(value = 100, message = "Score must be between 0 and 100")
    @Column(nullable = false)
    private Integer score;

    // ScreeningDecision — set by evaluate() in the service, not sent by the client
    @Pattern(regexp = "^(FIT|SIMILAR|SATURATED)$", message = "Decision must be FIT, SIMILAR or SATURATED")
    @Column(nullable = false, length = 20)
    private String decision;

    @NotNull(message = "Search radius is required")
    @Positive(message = "Search radius must be positive")
    @Column(nullable = false)
    private Double searchRadiusKm;

    @NotNull(message = "Competitors count is required")
    @PositiveOrZero(message = "Competitors count can't be negative")
    @Column(nullable = false)
    private Integer competitorsCount;

    // null when there is no competitor inside the radius
    @PositiveOrZero(message = "Distance can't be negative")
    private Double nearestCompetitorKm;

    @NotNull(message = "Scarcity result is required")
    @Column(nullable = false)
    private Boolean scarcityFound;

    @Column(columnDefinition = "text")
    private String feasibilitySummary;

    // Sets the decision from the check results:
    // no scarcity -> SATURATED, similar idea found -> SIMILAR, otherwise FIT
    public String evaluate() {
        if (!Boolean.TRUE.equals(scarcityFound)) {
            decision = "SATURATED";
        } else if (similarIdeaId != null) {
            decision = "SIMILAR";
        } else {
            decision = "FIT";
        }
        return decision;
    }
}