package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AcceptPartnershipTermsRequest {

    @NotNull
    private Boolean termsAccepted;

    @NotBlank
    private String signedName;

    private String signatureIp;
}
