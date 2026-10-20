package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "partnership_agreements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnershipAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "access_id", nullable = false, unique = true)
    private Integer accessId;

    @Column(name = "profit_share_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal profitSharePercent;

    @Column(name = "duration_years", nullable = false)
    private Integer durationYears;

    @Column(nullable = false)
    private String status;

    @Column(name = "signed_name")
    private String signedName;

    @Column(name = "signature_ip")
    private String signatureIp;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "share_starts_at")
    private LocalDate shareStartsAt;

    @Column(name = "share_ends_at")
    private LocalDate shareEndsAt;
}
