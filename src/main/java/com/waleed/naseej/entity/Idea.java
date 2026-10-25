package com.waleed.naseej.entity;

import com.waleed.naseej.enums.IdeaStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ideas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "neighborhood_id", nullable = false)
    private Neighborhood neighborhood;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IdeaStatus status = IdeaStatus.SCREENING;

    @Column(nullable = false)
    @Builder.Default
    private Integer membershipThreshold = 150;

    private LocalDateTime publishedAt;

    private LocalDateTime confirmedAt;

    // Exact spot of the idea; when empty the neighborhood center is used
    private Double latitude;

    private Double longitude;

    // Why the AI check rejected the idea (status REJECTED)
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // ---------- Unlock price quoted by the AI once, so checkout always matches what the investor saw ----------

    @Column(precision = 12, scale = 2)
    private BigDecimal unlockPrice;

    private Integer executionScore;

    private Integer successScore;

    private Integer opportunityScore;

    @Column(columnDefinition = "TEXT")
    private String priceReasoning;

    private LocalDateTime priceQuotedAt;

    // ---------- Admin moderation ----------

    // The status the idea had before an admin hid it, so un-hiding puts it back where it was
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IdeaStatus hiddenFromStatus;
}
