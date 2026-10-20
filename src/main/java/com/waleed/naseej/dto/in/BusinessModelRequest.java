package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessModelRequest {

    @NotNull(message = "Idea is required")
    private Integer ideaId;

    @NotEmpty(message = "Value proposition is required")
    private String valueProposition;

    @NotEmpty(message = "Target customers is required")
    private String targetCustomers;

    @NotEmpty(message = "Revenue streams is required")
    private String revenueStreams;

    @NotEmpty(message = "Cost structure is required")
    private String costStructure;

    @NotEmpty(message = "Channels is required")
    private String channels;

    @PositiveOrZero(message = "Minimum capital can't be negative")
    private BigDecimal estimatedCapitalMin;

    @PositiveOrZero(message = "Maximum capital can't be negative")
    private BigDecimal estimatedCapitalMax;

    private String risks;

    @AssertTrue(message = "Minimum capital can't be more than the maximum")
    private boolean isCapitalRangeValid() {
        return estimatedCapitalMin == null || estimatedCapitalMax == null
                || estimatedCapitalMin.compareTo(estimatedCapitalMax) <= 0;
    }
}
