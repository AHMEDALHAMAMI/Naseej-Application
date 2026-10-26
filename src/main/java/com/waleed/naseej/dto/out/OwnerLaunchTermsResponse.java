package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * What an idea's owner agrees to when they launch their own idea: the platform's service fee and how long it lasts.
 * The owner doesn't buy their own idea and doesn't need the full vote target.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLaunchTermsResponse {

    private Integer ideaId;
    private String ideaTitle;
    private String neighborhoodName;
    private String city;

    // Demand so far: shown as evidence, not a condition
    private Long votesCount;
    private Integer membershipThreshold;
    private Integer votePercent;

    // Platform service fee: a share of the project's net profit for a number of years. Null until the team sets it
    // The one-time service fee: amount before VAT, the VAT and what the owner pays
    private BigDecimal feeAmount;
    private BigDecimal feeVat;
    private BigDecimal feeTotal;
    // A fee already paid that hasn't started the project yet (the page can finish with it)
    private Integer paidPaymentId;
    private String termsVersion;
    private Integer openingDeadlineDays;

    // Whether the owner can sign now, and why not when they can't
    private Boolean available;
    private String blockedReason;
}
