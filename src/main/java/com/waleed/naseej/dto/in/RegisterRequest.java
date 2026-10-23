package com.waleed.naseej.dto.in;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

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

    // Optional, Saudi mobile format: 9665XXXXXXXX
    @Pattern(regexp = "^9665\\d{8}$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    // Required for residents, optional for investors
    private Integer neighborhoodId;

    // Only RESIDENT or INVESTOR are accepted at sign-up
    @NotNull(message = "Role is required (RESIDENT or INVESTOR)")
    private Role role;

    private Boolean whatsappOptIn;
}