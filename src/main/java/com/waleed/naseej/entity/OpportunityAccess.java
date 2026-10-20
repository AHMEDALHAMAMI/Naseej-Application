package com.waleed.naseej.entity;

import com.waleed.naseej.enums.AccessType;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"investor_id", "idea_id"}))
public class OpportunityAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "Investor is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id", nullable = false)
    private User investor;

    @NotNull(message = "Idea is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", unique = true)
    private Payment payment;

    @NotNull(message = "Access type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessType accessType;

    private LocalDateTime priorityUntil;

    @AssertTrue(message = "PAID access needs a payment, PARTNERSHIP access must not have one")
    private boolean isPaymentConsistent() {
        return accessType == AccessType.PAID ? payment != null : payment == null;
    }
}
