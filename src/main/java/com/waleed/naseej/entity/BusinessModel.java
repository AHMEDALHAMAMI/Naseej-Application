package com.waleed.naseej.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Idea is required")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, unique = true)
    private Idea idea;

    @NotEmpty(message = "Value proposition is required")
    @Column(columnDefinition = "text not null")
    private String valueProposition;

    @NotEmpty(message = "Target customers is required")
    @Column(columnDefinition = "text not null")
    private String targetCustomers;

    @NotEmpty(message = "Revenue streams is required")
    @Column(columnDefinition = "text not null")
    private String revenueStreams;

    @NotEmpty(message = "Cost structure is required")
    @Column(columnDefinition = "text not null")
    private String costStructure;

    @NotEmpty(message = "Channels is required")
    @Column(columnDefinition = "text not null")
    private String channels;

    @PositiveOrZero(message = "Minimum capital can't be negative")
    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedCapitalMin;

    @PositiveOrZero(message = "Maximum capital can't be negative")
    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedCapitalMax;

    @Column(columnDefinition = "text")
    private String risks;

    @AssertTrue(message = "Minimum capital can't be more than the maximum")
    private boolean isCapitalRangeValid() {
        return estimatedCapitalMin == null || estimatedCapitalMax == null
                || estimatedCapitalMin.compareTo(estimatedCapitalMax) <= 0;
    }
}
