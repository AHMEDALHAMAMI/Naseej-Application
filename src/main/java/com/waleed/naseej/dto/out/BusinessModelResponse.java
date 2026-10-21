package com.waleed.naseej.dto.out;

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
public class BusinessModelResponse {
    private Integer id;
    private Integer ideaId;
    private String valueProposition;
    private String targetCustomers;
    private String revenueStreams;
    private String costStructure;
    private String channels;
    private BigDecimal estimatedCapitalMin;
    private BigDecimal estimatedCapitalMax;
    private String risks;
}
