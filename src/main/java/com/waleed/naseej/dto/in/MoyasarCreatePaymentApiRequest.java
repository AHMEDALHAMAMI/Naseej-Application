package com.waleed.naseej.dto.in;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoyasarCreatePaymentApiRequest {

    @JsonProperty("given_id")
    private String givenId;

    private Integer amount;
    private String currency;
    private String description;

    @JsonProperty("callback_url")
    private String callbackUrl;

    private MoyasarPaymentSourceApiRequest source;
}
