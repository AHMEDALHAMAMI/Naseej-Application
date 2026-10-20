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
public class FeasibilityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Idea is required")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, unique = true)
    private Idea idea;

    @NotNull(message = "Gap score is required")
    @Min(value = 0, message = "Gap score must be between 0 and 100")
    @Max(value = 100, message = "Gap score must be between 0 and 100")
    @Column(nullable = false)
    private Integer gapScore;

    private Double memberGrowthPercent;

    @Size(max = 50, message = "Peak demand time must be at most 50 characters")
    @Column(length = 50)
    private String peakDemandTime;

    @NotEmpty(message = "Recommendation is required")
    @Column(columnDefinition = "text not null")
    private String recommendation;

    @Column(columnDefinition = "text")
    private String risks;
}
