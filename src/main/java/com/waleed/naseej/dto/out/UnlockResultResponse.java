package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.AccessType;

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
public class UnlockResultResponse {

    private Integer ideaId;
    private Integer accessId;
    private AccessType accessType;
    private Integer contractId;
    private String contractNumber;
    private String message;
}
