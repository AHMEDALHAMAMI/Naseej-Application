package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin adds a member. The account is ready to use: email verified and active. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
    private String password;

    @Pattern(regexp = "^(9665\\d{8})?$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    @NotNull(message = "Choose the membership")
    private Role role;

    // Required for residents
    private Integer neighborhoodId;

    @Pattern(regexp = "^(\\d{10})?$", message = "Commercial registration number must be 10 digits")
    private String crNumber;

    // Investors only; PENDING when empty
    private InvestorStatus investorStatus;
}
