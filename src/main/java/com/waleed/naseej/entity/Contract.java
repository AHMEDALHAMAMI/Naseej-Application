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

    // The idea owner's discount once the project opens, in percent. (The founding members' discount was removed;
    // FoundingDiscountColumnFix makes its old column optional in databases created before.)
    @Column(name = "idea_owner_discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal ideaOwnerDiscount;

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

    // Benefits the investor commits to beyond the discount, e.g. "the first month free for the idea owner"
    @Column(name = "benefits_note", length = 500)
    private String benefitsNote;

    // Set when the investor saves the idea owner's benefits; signing is allowed only after this
    @Column(name = "benefits_set_at")
    private LocalDateTime benefitsSetAt;

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
