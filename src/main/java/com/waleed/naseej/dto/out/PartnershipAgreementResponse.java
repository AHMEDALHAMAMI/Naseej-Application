package com.waleed.naseej.dto.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class PartnershipAgreementResponse {
    private Integer id;
    private Integer accessId;
    private BigDecimal profitSharePercent;
    private Integer durationYears;
    private String status;
    private String signedName;
    private String signatureIp;
    private LocalDateTime signedAt;
    private LocalDate validUntil;
    private LocalDate shareStartsAt;
    private LocalDate shareEndsAt;
}
