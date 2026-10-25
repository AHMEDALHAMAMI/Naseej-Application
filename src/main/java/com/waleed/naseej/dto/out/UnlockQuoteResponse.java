package com.waleed.naseej.dto.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The AI-priced upfront amount and the success partnership alternative. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnlockQuoteResponse {

    private Integer ideaId;
    private String ideaTitle;
    private BigDecimal price;
    private BigDecimal vat;
    private BigDecimal total;
    private Integer executionScore;
    private Integer successScore;
    private Integer opportunityScore;
    private String reasoning;
    private LocalDateTime quotedAt;
    // Success partnership: share of the project's net profit that goes to the founding member
    private BigDecimal partnershipPercent;
    private Integer partnershipYears;
}
