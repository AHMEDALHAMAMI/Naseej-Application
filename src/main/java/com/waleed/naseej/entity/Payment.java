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
