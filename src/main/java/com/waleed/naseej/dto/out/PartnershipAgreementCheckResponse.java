package com.waleed.naseej.dto.out;

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
public class PartnershipAgreementCheckResponse {

    private Integer accessId;
    private Integer agreementId;
    private boolean agreementExists;
    private boolean termsAccepted;
}
