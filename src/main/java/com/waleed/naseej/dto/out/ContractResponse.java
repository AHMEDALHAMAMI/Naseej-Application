package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.ContractStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private Integer id;
    private String contractNumber;
    private Integer ideaId;
    private Integer investorId;
    private Integer ideaOwnerId;
    private Integer accessId;
    private BigDecimal ideaOwnerDiscount;
    private ContractStatus status;
    private LocalDate openingDeadline;
    private String pdfUrl;
    private LocalDateTime emailSentAt;
    private LocalDateTime activatedAt;
    private LocalDateTime openedAt;
}
