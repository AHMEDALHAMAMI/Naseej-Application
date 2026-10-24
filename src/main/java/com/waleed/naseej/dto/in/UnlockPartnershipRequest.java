package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
public class UnlockPartnershipRequest {

    @NotNull(message = "Accept the partnership terms to continue")
    @AssertTrue(message = "Accept the partnership terms to continue")
    private Boolean termsAccepted;

    // Typed full name as the signature
    @NotBlank(message = "Type your full name to sign")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String signedName;
}
