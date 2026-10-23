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
public class ContractSigningCheckResponse {

    private Integer contractId;
    private boolean investorSigned;
    private boolean ideaOwnerSigned;
    private boolean bothPartiesSigned;
    private boolean investorTermsAccepted;
    private boolean ideaOwnerTermsAccepted;
    private boolean bothPartiesTermsAccepted;
}
