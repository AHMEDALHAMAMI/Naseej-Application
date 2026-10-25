package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What the investor commits to give the founding members and the idea owner once the project opens. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractBenefitsRequest {

    // Permanent discount for the founding members, in percent
    @NotNull(message = "Founding members' discount is required")
    @DecimalMin(value = "0.0", message = "Discount can't be negative")
    @DecimalMax(value = "100.0", message = "Discount can't be more than 100%")
    @Digits(integer = 3, fraction = 2, message = "Discount can have at most 2 decimals")
    private BigDecimal foundingMemberDiscount;

    // The idea owner's discount, in percent; must be at least the founding members' one
    @NotNull(message = "Idea owner's discount is required")
    @DecimalMin(value = "0.0", message = "Discount can't be negative")
    @DecimalMax(value = "100.0", message = "Discount can't be more than 100%")
    @Digits(integer = 3, fraction = 2, message = "Discount can have at most 2 decimals")
    private BigDecimal ideaOwnerDiscount;

    // Other benefits in words, e.g. "Priority booking for founding members"
    @Size(max = 500, message = "Benefits note must be at most 500 characters")
    private String benefitsNote;
}
