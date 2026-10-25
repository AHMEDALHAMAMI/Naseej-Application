package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The logged-in user edits their profile. Send only what changes; empty fields are left as they are.
 * Email, password and role have their own flows (OTP, reset-password, select-role).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    @Pattern(regexp = "^9665\\d{8}$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    private Boolean whatsappOptIn;

    // Residents only
    private Integer neighborhoodId;

    // ---------- Investors only. A new CR number sends the account back to admin review ----------
    @Pattern(regexp = "^\\d{10}$", message = "Commercial registration number must be 10 digits")
    private String crNumber;

    @Pattern(regexp = "^(UP_TO_300K|FROM_300K_TO_700K|ABOVE_700K)$",
            message = "Investment budget must be UP_TO_300K, FROM_300K_TO_700K or ABOVE_700K")
    private String investmentBudget;

    @Pattern(regexp = "^(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION)(,(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION))*$",
            message = "Investment fields must be a comma-separated list of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION")
    private String investmentFields;
}
