package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One member in the admin's members list, with where they live and what they did on the platform. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {

    private Integer id;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private AuthProvider authProvider;
    private Boolean emailVerified;
    private Boolean enabled;
    private Integer neighborhoodId;
    private String neighborhoodName;
    private String city;
    private InvestorStatus investorStatus;
    private String crNumber;
    private Long ideasCount;
    private Long votesCount;
}
