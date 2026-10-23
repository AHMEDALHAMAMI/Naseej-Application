package com.waleed.naseej.dto.in;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MoyasarPaymentSourceApiRequest {

    private String type;
    private String name;
    private String number;
    private String month;
    private String year;
    private String cvc;
}
