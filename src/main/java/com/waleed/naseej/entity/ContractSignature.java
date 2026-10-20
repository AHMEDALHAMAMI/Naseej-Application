package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_signatures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signer_id", nullable = false)
    private User signer;

    @Column(name = "signer_role", nullable = false)
    private String signerRole;

    @Column(name = "typed_name", nullable = false)
    private String typedName;

    @Column(name = "signature_image_uri", length = 512)
    private String signatureImageUri;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;
}
