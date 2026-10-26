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

/** The idea's owner signs the platform service agreement to launch their own idea. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLaunchRequest {

    @NotNull(message = "Accept the service agreement to continue")
    @AssertTrue(message = "Accept the service agreement to continue")
    private Boolean termsAccepted;

    // Typed full name as the signature
    @NotBlank(message = "Type your full name to sign")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String signedName;

    // The terms version the owner read, so terms that changed in the meantime are never signed by accident
    @NotBlank(message = "The agreement version is missing. Reload the page and try again")
    @Size(max = 40, message = "The agreement version is too long")
    private String termsVersion;
}
