package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.ContractStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ContractRequest {

    @NotBlank
    private String contractNumber;
    @NotNull
    private Integer ideaId;
    @NotNull
    private Integer investorId;
    @NotNull
    private Integer ideaOwnerId;
    @NotNull
    private Integer accessId;
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal ideaOwnerDiscount;
    private LocalDate openingDeadline;
    private ContractStatus status;
    private String pdfUrl;
    private LocalDateTime emailSentAt;
    private LocalDateTime activatedAt;
    private LocalDateTime openedAt;

}
