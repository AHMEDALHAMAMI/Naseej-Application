package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Integer id;
    private String fullName;
    private String email;
    private String phone;
    private AuthProvider authProvider;
    private String googleId;
    private Role role;
    private Integer neighborhoodId;
    private Boolean emailVerified;
    private Boolean whatsappOptIn;
    private Boolean enabled;
    private Double latitude;
    private Double longitude;
    private String crNumber;
    private String investmentBudget;
    private String investmentFields;
    private InvestorStatus investorStatus;
}
