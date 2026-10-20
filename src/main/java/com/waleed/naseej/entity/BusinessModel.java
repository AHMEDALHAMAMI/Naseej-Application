package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "business_models")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false, unique = true)
    private Idea idea;

    @Column(columnDefinition = "text not null")
    private String valueProposition;

    @Column(columnDefinition = "text not null")
    private String targetCustomers;

    @Column(columnDefinition = "text not null")
    private String revenueStreams;

    @Column(columnDefinition = "text not null")
    private String costStructure;

    @Column(columnDefinition = "text not null")
    private String channels;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedCapitalMin;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedCapitalMax;

    @Column(columnDefinition = "text")
    private String risks;
}
