package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class DraftContractRequest {

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

    private String pdfUrl;
}
