package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.waleed.naseej.service.PhoneNumbers;
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

    @Pattern(regexp = PhoneNumbers.REGEX_OR_EMPTY, message = PhoneNumbers.MESSAGE)
    private String phone;

    @NotNull(message = "Choose the membership")
    private Role role;

    private Integer neighborhoodId;

    @Pattern(regexp = "^(7\\d{9})?$", message = "Commercial registration number must be 10 digits starting with 7")
    private String crNumber;

    private InvestorStatus investorStatus;

    private Boolean emailVerified;
}
