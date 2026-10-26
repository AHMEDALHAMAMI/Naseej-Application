package com.waleed.naseej.dto.in;

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

    // Optional and not required: the investor pages show the whole idea, so there is no confidentiality step
    // before paying. Kept so older clients that still send it keep working.
    private Boolean ndaAccepted;
}
