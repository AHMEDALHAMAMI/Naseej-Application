package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feasibility_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeasibilityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, unique = true)
    private Idea idea;

    @Column(nullable = false)
    private Integer gapScore;

    private Double memberGrowthPercent;

    @Column(length = 50)
    private String peakDemandTime;

    @Column(columnDefinition = "text not null")
    private String recommendation;

    @Column(columnDefinition = "text")
    private String risks;
}
