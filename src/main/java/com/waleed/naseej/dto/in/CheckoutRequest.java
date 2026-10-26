package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.AssertTrue;
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
public class CheckoutRequest {

    @NotNull(message = "Accept the confidentiality terms to continue")
    @AssertTrue(message = "Accept the confidentiality terms to continue")
    private Boolean ndaAccepted;
}
