package com.waleed.naseej.entity;

import com.waleed.naseej.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id", nullable = false)
    private User investor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_owner_id", nullable = false)
    private User ideaOwner;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_id", nullable = false, unique = true)
    private OpportunityAccess access;

    @Column(name = "founding_member_discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal foundingMemberDiscount;

    @Column(name = "idea_owner_discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal ideaOwnerDiscount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "opening_deadline")
    private LocalDate openingDeadline;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;
}
