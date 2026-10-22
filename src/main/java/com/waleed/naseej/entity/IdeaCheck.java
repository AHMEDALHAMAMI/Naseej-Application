package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false)
    private Integer score;

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

    // Google Places type the AI chose for the idea (e.g. "cafe"), reused when competitors are refreshed
    @Column(length = 50)
    private String placeType;
}
