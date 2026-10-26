package com.waleed.naseej.entity;

import com.waleed.naseej.enums.PaymentMethod;
import com.waleed.naseej.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String purpose;

    // The idea an unlock payment is for, so a payment can't be reused for another (pricier) idea
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idea_id")
    private Idea idea;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vat;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "SAR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(name = "moyasar_payment_id", nullable = false, unique = true)
    private String moyasarPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "invoice_url", length = 512)
    private String invoiceUrl;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // Owner launch: the agreement the investor signed before paying. It becomes the contract's signature
    // once Moyasar confirms the payment
    @Column(name = "signer_name", length = 120)
    private String signerName;

    @Column(name = "signer_ip", length = 64)
    private String signerIp;

    @Column(name = "terms_version", length = 40)
    private String termsVersion;

    public void confirm() {
        if (this.status == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Refunded payments cannot be confirmed");
        }
        this.status = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void refund() {
        if (this.status != PaymentStatus.PAID) {
            throw new IllegalStateException("Only paid payments can be refunded");
        }
        this.status = PaymentStatus.REFUNDED;
    }
}
