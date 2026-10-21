package com.waleed.naseej.dto.in;

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
public class ContractSignatureRequest {

    private Integer contractId;
    private Integer signerId;
    private String signerRole;
    private String typedName;
    private String signatureImageUri;
    private String ipAddress;
    private Boolean otpVerified;
    private Boolean termsAccepted;
    private LocalDateTime signedAt;

}
