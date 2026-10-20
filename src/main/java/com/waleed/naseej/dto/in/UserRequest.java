package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.Role;
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
public class UserRequest {

    private String fullName;
    private String email;
    private String password;
    private String phone;
    private AuthProvider authProvider;
    private String googleId;
    private Role role;
    private Integer neighborhoodId;
    private Boolean emailVerified;
    private Boolean whatsappOptIn;
    private Boolean enabled;

}
