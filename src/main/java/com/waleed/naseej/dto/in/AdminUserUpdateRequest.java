package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin edits a member's profile and membership. The email can't be changed here (it is the login). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    @Pattern(regexp = "^(9665\\d{8})?$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    @NotNull(message = "Choose the membership")
    private Role role;

    private Integer neighborhoodId;

    @Pattern(regexp = "^(\\d{10})?$", message = "Commercial registration number must be 10 digits")
    private String crNumber;

    private InvestorStatus investorStatus;

    private Boolean emailVerified;
}
