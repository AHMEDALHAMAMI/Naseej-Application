package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "idea_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdeaCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, unique = true)
    private Idea idea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "similar_idea_id")
    private Idea similarIdea;

    // The headline score: the rounded average of the three parts below (older checks have only the headline)
    @Column(nullable = false)
    private Integer score;

    // 0-100 each: shortage in the area, competitors nearby (higher = fewer) and expected demand
    private Integer shortageScore;

    private Integer competitionScore;

    private Integer demandScore;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(nullable = false)
    private Double searchRadiusKm;

    @Column(nullable = false)
    private Integer competitorsCount;

    private Double nearestCompetitorKm;

    @Column(nullable = false)
    private Boolean scarcityFound;

    @Column(columnDefinition = "text")
    private String feasibilitySummary;

    // When the check ran; a draft whose check is too old is checked again before it can be published
    private LocalDateTime checkedAt;

    // Google Places type the AI chose for the idea (e.g. "cafe"), reused when competitors are refreshed
    @Column(length = 50)
    private String placeType;
}
