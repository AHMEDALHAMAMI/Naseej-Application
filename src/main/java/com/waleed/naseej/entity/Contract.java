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

    // No discount for the idea owner any more: always 0. Kept because existing databases have the column as
    // NOT NULL. (The founding members' discount was removed too; FoundingDiscountColumnFix makes its old column
    // optional in databases created before.)
    @Column(name = "idea_owner_discount", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal ideaOwnerDiscount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    // Owner launch (OWNER_SERVICE): the platform's service fee the owner signed, as a share of the project's
    // net profit for a number of years, and the version of the terms they read. Null for investor agreements.
    @Column(name = "platform_fee_percent", precision = 5, scale = 2)
    private BigDecimal platformFeePercent;

    @Column(name = "platform_fee_years")
    private Integer platformFeeYears;

    @Column(name = "terms_version", length = 40)
    private String termsVersion;

    // Set when the contract becomes ACTIVE (ContractFlowService.OPENING_DEADLINE_DAYS later); moved once by an extension
    @Column(name = "opening_deadline")
    private LocalDate openingDeadline;

    // Until the investor signs, the contract holds the idea only until this day (SIGNING_DEADLINE_DAYS after adoption)
    @Column(name = "signing_deadline")
    private LocalDate signingDeadline;

    // When the investor used their one extension of the opening deadline (null = not used)
    @Column(name = "extended_at")
    private LocalDateTime extendedAt;

    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    public void sign(ContractSignature signature) {
        signature.setContract(this);
        if ("INVESTOR".equalsIgnoreCase(signature.getSignerRole())
                && this.status == ContractStatus.DRAFT) {
            this.status = ContractStatus.INVESTOR_SIGNED;
        }
    }

    public void activate() {
        if (this.status == ContractStatus.TERMINATED) {
            throw new IllegalStateException("Terminated contracts cannot be activated");
        }
        LocalDateTime now = LocalDateTime.now();
        this.status = ContractStatus.ACTIVE;
        this.activatedAt = now;
        this.emailSentAt = now;
    }

    public void terminate() {
        this.status = ContractStatus.TERMINATED;
    }

    public ProjectUpdate addUpdate(String stage, String note) {
        return ProjectUpdate.builder()
                .contract(this)
                .stage(stage)
                .note(note)
                .build();
    }
}
