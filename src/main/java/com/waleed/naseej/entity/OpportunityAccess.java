package com.waleed.naseej.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    // FK -> User.id (role INVESTOR)
    @NotNull(message = "Investor id is required")
    @Column(name = "investor_id", nullable = false)
    private Integer investorId;

    // FK -> Idea.id
    @NotNull(message = "Idea id is required")
    @Column(name = "idea_id", nullable = false)
    private Integer ideaId;

    // FK -> Payment.id, only for PAID access (PARTNERSHIP has no payment)
    @Column(name = "payment_id", unique = true)
    private Integer paymentId;

    // AccessType
    @NotEmpty(message = "Access type is required")
    @Pattern(regexp = "^(PAID|PARTNERSHIP)$", message = "Access type must be PAID or PARTNERSHIP")
    @Column(nullable = false, length = 20)
    private String accessType;

    private LocalDateTime priorityUntil;

    @AssertTrue(message = "PAID access needs a payment, PARTNERSHIP access must not have one")
    private boolean isPaymentConsistent() {
        return "PAID".equals(accessType) == (paymentId != null);
    }
}
