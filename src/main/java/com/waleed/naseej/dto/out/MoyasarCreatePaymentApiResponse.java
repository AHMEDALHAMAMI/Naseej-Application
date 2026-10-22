package com.waleed.naseej.dto.out;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MoyasarCreatePaymentApiResponse {

    private String id;
    private String status;
    private Integer amount;
    private String currency;
    private MoyasarPaymentSourceApiResponse source;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MoyasarPaymentSourceApiResponse {

        private String type;

        @JsonProperty("transaction_url")
        private String transactionUrl;
    }
}
