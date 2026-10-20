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
public class FoundingMemberRequest {

    private Integer userId;
    private Integer ideaId;
    private Integer memberNumber;
    private Boolean ideaOwner;
    private Boolean consentGiven;

}
