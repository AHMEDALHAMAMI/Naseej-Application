package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Signing inside the platform: the typed full name, the drawn signature and the code emailed by
 * POST /projects/{contractId}/sign-otp. The signer and the IP come from the token and the request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignContractRequest {

    // Must match the full name on the account
    @NotBlank(message = "Type your full name to sign")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String typedName;

    // The drawn signature from the signature pad, as data:image/png;base64,...
    @NotBlank(message = "Draw your signature")
    @Size(max = 300000, message = "The signature image is too large")
    private String signatureImage;

    @NotNull(message = "Accept the contract terms to sign")
    @AssertTrue(message = "Accept the contract terms to sign")
    private Boolean termsAccepted;

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
    private String code;
}
